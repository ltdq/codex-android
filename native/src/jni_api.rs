use crate::Bridge;
use anyhow::{Result, anyhow, bail};
use jni::JNIEnv;
use jni::objects::{JByteArray, JObject};
use jni::sys::{jbyteArray, jint, jlong};
use std::collections::HashMap;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::Duration;

static BRIDGES: OnceLock<Mutex<HashMap<i64, Arc<Bridge>>>> = OnceLock::new();
static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);

fn registry() -> &'static Mutex<HashMap<i64, Arc<Bridge>>> {
    BRIDGES.get_or_init(Mutex::default)
}

fn bridge(handle: jlong) -> Result<Arc<Bridge>> {
    registry()
        .lock()
        .map_err(|_| anyhow!("runtime registry poisoned"))?
        .get(&handle)
        .cloned()
        .ok_or_else(|| anyhow!("invalid or closed runtime handle"))
}

fn boundary<T: Default>(
    env: &mut JNIEnv<'_>,
    operation: impl FnOnce(&mut JNIEnv<'_>) -> Result<T>,
) -> T {
    match catch_unwind(AssertUnwindSafe(|| operation(env))) {
        Ok(Ok(result)) => result,
        Ok(Err(error)) => {
            let _ = env.throw_new("java/lang/IllegalStateException", format!("{error:#}"));
            T::default()
        }
        Err(_) => {
            let _ = env.throw_new(
                "java/lang/IllegalStateException",
                "Codex native runtime panicked",
            );
            T::default()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_cy_codexui_runtime_NativeBridge_nativeStart(
    mut env: JNIEnv,
    _object: JObject,
    config: JByteArray,
) -> jlong {
    boundary(&mut env, |env| {
        let config = env.convert_byte_array(&config)?;
        let runtime = std::thread::Builder::new()
            .name("codex-start".into())
            .stack_size(16 * 1024 * 1024)
            .spawn(move || Bridge::start(&config))?
            .join()
            .map_err(|_| anyhow!("Codex startup thread panicked"))??;
        let runtime = Arc::new(runtime);
        let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed);
        registry()
            .lock()
            .map_err(|_| anyhow!("runtime registry poisoned"))?
            .insert(handle, runtime);
        Ok(handle)
    })
}

/// `kind` is `JsonRpcMessageKind`: 0 request, 1 notification, 2 response, 3 error.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_cy_codexui_runtime_NativeBridge_nativeSend(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    kind: jint,
    message: JByteArray,
) {
    boundary(&mut env, |env| {
        let message = env.convert_byte_array(&message)?;
        let runtime = bridge(handle)?;
        match kind {
            0 => runtime.send_request(&message),
            1 => runtime.send_notification(&message),
            2 => runtime.send_response(&message),
            3 => runtime.send_error(&message),
            _ => bail!("invalid JSON-RPC message kind: {kind}"),
        }
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_cy_codexui_runtime_NativeBridge_nativeReceive(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    timeout_millis: jint,
) -> jbyteArray {
    boundary(&mut env, |env| {
        let timeout = Duration::from_millis(timeout_millis.max(0) as u64);
        match bridge(handle)?.receive(timeout)? {
            Some(message) => Ok(env.byte_array_from_slice(&message)?.into_raw()),
            None => Ok(std::ptr::null_mut()),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_cy_codexui_runtime_NativeBridge_nativeStop(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
) {
    boundary(&mut env, |_env| {
        let runtime = registry()
            .lock()
            .map_err(|_| anyhow!("runtime registry poisoned"))?
            .remove(&handle);
        if let Some(runtime) = runtime {
            runtime.close()?;
        }
        Ok(())
    });
}
