use anyhow::{Context, Result, anyhow, bail};
use codex_app_server::in_process::{
    self, InProcessClientHandle, InProcessServerEvent, InProcessStartArgs,
};
use codex_app_server_protocol::{
    ClientInfo, ClientNotification, ClientRequest, InitializeCapabilities, InitializeParams,
    JSONRPCErrorError, RequestId,
};
use codex_arg0::Arg0DispatchPaths;
use codex_config::{CloudConfigBundleLoader, LoaderOverrides, NoopThreadConfigLoader};
use codex_core::config::{ConfigBuilder, ConfigOverrides};
use codex_exec_server::{EnvironmentManager, ExecServerRuntimePaths};
use codex_feedback::CodexFeedback;
use codex_protocol::protocol::SessionSource;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::BTreeMap;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::Duration;
use tokio::runtime::Runtime;
use tokio::sync::{mpsc, oneshot};
use tokio::task::JoinSet;

const QUEUE_CAPACITY: usize = 256;
const MAX_MESSAGE_BYTES: usize = 16 * 1024 * 1024;
static ENVIRONMENT: OnceLock<BTreeMap<String, String>> = OnceLock::new();
static START_LOCK: Mutex<()> = Mutex::new(());

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct StartConfig {
    codex_home: PathBuf,
    cwd: PathBuf,
    codex_self_exe: PathBuf,
    #[cfg(target_os = "android")]
    toolchain_root: PathBuf,
    #[cfg(target_os = "android")]
    shell_path: PathBuf,
    #[serde(default)]
    env: BTreeMap<String, String>,
}

/// One client message after exactly one serde parse; no intermediate `serde_json::Value`.
enum Command {
    Request(Box<ClientRequest>),
    Notification(Box<ClientNotification>),
    ServerResponse { id: RequestId, result: Value },
    ServerError { id: RequestId, error: JSONRPCErrorError },
}

/// `{"id": …, "result": …}`, the client's answer to an app-server request.
#[derive(Deserialize)]
struct ClientServerResponse {
    id: RequestId,
    #[serde(default)]
    result: Value,
}

/// `{"id": …, "error": …}`, the client's rejection of an app-server request.
#[derive(Deserialize)]
struct ClientServerError {
    id: RequestId,
    error: JSONRPCErrorError,
}

/// Outgoing `{"id": …, "result": …}`; `result` is borrowed to avoid copying the app-server's tree.
#[derive(Serialize)]
struct ServerResponseEnvelope<'a> {
    id: &'a RequestId,
    result: &'a Value,
}

/// Outgoing `{"id": …, "error": …}`.
#[derive(Serialize)]
struct ServerErrorEnvelope<'a> {
    id: &'a RequestId,
    error: &'a JSONRPCErrorError,
}

#[derive(Serialize)]
struct LaggedNotification {
    method: &'static str,
    params: LaggedParams,
}

#[derive(Serialize)]
struct LaggedParams {
    skipped: usize,
}

#[derive(Serialize)]
struct TransportErrorNotification<'a> {
    method: &'static str,
    params: TransportErrorParams<'a>,
}

#[derive(Serialize)]
struct TransportErrorParams<'a> {
    message: &'a str,
}

/// Owns one real, in-process app-server and bounded byte-queue transport.
pub struct Bridge {
    runtime: Option<Runtime>,
    commands: mpsc::Sender<Command>,
    events: Mutex<mpsc::Receiver<Vec<u8>>>,
    stop: Mutex<Option<oneshot::Sender<()>>>,
    worker: Mutex<Option<tokio::task::JoinHandle<()>>>,
    stopped: AtomicBool,
}

impl Bridge {
    pub fn start(config: &[u8]) -> Result<Self> {
        let _guard = START_LOCK
            .lock()
            .map_err(|_| anyhow!("startup lock poisoned"))?;
        let mut settings: StartConfig = serde_json::from_slice(config)?;
        #[cfg(target_os = "android")]
        {
            let expected_shell = settings.toolchain_root.join("bin/bash");
            if !settings.toolchain_root.is_absolute() || settings.shell_path != expected_shell {
                bail!("Android shell must be the packaged toolchain's bin/bash");
            }
            let shell = settings.shell_path.to_string_lossy();
            if settings.env.get("SHELL").map(String::as_str) != Some(shell.as_ref())
                || settings.env.get("CODEX_SHELL").map(String::as_str) != Some(shell.as_ref())
            {
                bail!("Android shell environment does not match its configured toolchain");
            }
            let bin = settings.toolchain_root.join("bin");
            let search_path = settings
                .env
                .get("PATH")
                .context("Android toolchain PATH missing")?;
            if std::env::split_paths(search_path).next().as_ref() != Some(&bin) {
                bail!("Android PATH must start with the packaged toolchain bin directory");
            }
            codex_shell_command::android::configure(settings.shell_path.clone())?;
        }
        for path in [
            &settings.codex_home,
            &settings.cwd,
            &settings.codex_self_exe,
        ] {
            if !path.is_absolute() {
                bail!("runtime path must be absolute: {}", path.display());
            }
        }
        if !settings.codex_self_exe.is_file() {
            bail!(
                "Codex helper is missing: {}",
                settings.codex_self_exe.display()
            );
        }
        std::fs::create_dir_all(&settings.codex_home)?;
        std::fs::create_dir_all(&settings.cwd)?;
        settings.env.insert(
            "CODEX_HOME".into(),
            settings.codex_home.to_string_lossy().into_owned(),
        );
        for (key, value) in &settings.env {
            if key.is_empty() || key.contains(['=', '\0']) || value.contains('\0') {
                bail!("invalid runtime environment key or value");
            }
        }
        if let Some(initial) = ENVIRONMENT.get() {
            if initial != &settings.env {
                bail!("runtime environment changed; restart the application to apply it");
            }
        } else {
            for (key, expected) in &settings.env {
                if std::env::var(key).as_ref() != Ok(expected) {
                    bail!(
                        "runtime environment {key} must be installed before loading the native library"
                    );
                }
            }
            let _ = ENVIRONMENT.set(settings.env.clone());
        }
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(4)
            .thread_stack_size(8 * 1024 * 1024)
            .enable_all()
            .build()?;
        let client = runtime.block_on(start_client(settings))?;
        let (commands, command_rx) = mpsc::channel(QUEUE_CAPACITY);
        let (event_tx, events) = mpsc::channel(QUEUE_CAPACITY);
        let (stop, stop_rx) = oneshot::channel();
        let worker = runtime.spawn(run(client, command_rx, event_tx, stop_rx));
        Ok(Self {
            runtime: Some(runtime),
            commands,
            events: Mutex::new(events),
            stop: Mutex::new(Some(stop)),
            worker: Mutex::new(Some(worker)),
            stopped: AtomicBool::new(false),
        })
    }

    pub fn send_request(&self, message: &[u8]) -> Result<()> {
        check_message_size(message)?;
        let request = serde_json::from_slice(message).context("invalid JSON-RPC request")?;
        self.enqueue(Command::Request(Box::new(request)))
    }

    pub fn send_notification(&self, message: &[u8]) -> Result<()> {
        check_message_size(message)?;
        let notification = serde_json::from_slice(message).context("invalid JSON-RPC notification")?;
        self.enqueue(Command::Notification(Box::new(notification)))
    }

    pub fn send_response(&self, message: &[u8]) -> Result<()> {
        check_message_size(message)?;
        let ClientServerResponse { id, result } =
            serde_json::from_slice(message).context("invalid JSON-RPC response")?;
        self.enqueue(Command::ServerResponse { id, result })
    }

    pub fn send_error(&self, message: &[u8]) -> Result<()> {
        check_message_size(message)?;
        let ClientServerError { id, error } =
            serde_json::from_slice(message).context("invalid JSON-RPC error")?;
        self.enqueue(Command::ServerError { id, error })
    }

    fn enqueue(&self, command: Command) -> Result<()> {
        if self.stopped.load(Ordering::Acquire) {
            bail!("Codex runtime is closed");
        }
        self.commands
            .try_send(command)
            .context("Codex command queue unavailable")
    }

    pub fn receive(&self, timeout: Duration) -> Result<Option<Vec<u8>>> {
        let mut events = self
            .events
            .lock()
            .map_err(|_| anyhow!("event queue poisoned"))?;
        let runtime = self
            .runtime
            .as_ref()
            .ok_or_else(|| anyhow!("runtime released"))?;
        match runtime.block_on(async {
            tokio::time::timeout(timeout.min(Duration::from_secs(30)), events.recv()).await
        }) {
            Ok(Some(message)) => Ok(Some(message)),
            Ok(None) => bail!("Codex event stream closed"),
            Err(_) => Ok(None),
        }
    }

    pub fn close(&self) -> Result<()> {
        if self.stopped.swap(true, Ordering::AcqRel) {
            return Ok(());
        }
        if let Some(stop) = self
            .stop
            .lock()
            .map_err(|_| anyhow!("stop lock poisoned"))?
            .take()
        {
            let _ = stop.send(());
        }
        self.events
            .lock()
            .map_err(|_| anyhow!("event queue poisoned"))?
            .close();
        if let Some(mut worker) = self
            .worker
            .lock()
            .map_err(|_| anyhow!("worker lock poisoned"))?
            .take()
        {
            let runtime = self
                .runtime
                .as_ref()
                .ok_or_else(|| anyhow!("runtime released"))?;
            runtime.block_on(async {
                if tokio::time::timeout(Duration::from_secs(45), &mut worker)
                    .await
                    .is_err()
                {
                    worker.abort();
                    let _ = worker.await;
                }
            });
        }
        Ok(())
    }
}

impl Drop for Bridge {
    fn drop(&mut self) {
        let _ = self.close();
        if let Some(runtime) = self.runtime.take() {
            runtime.shutdown_timeout(Duration::from_secs(5));
        }
    }
}

fn check_message_size(message: &[u8]) -> Result<()> {
    if message.len() > MAX_MESSAGE_BYTES {
        bail!("JSON-RPC message exceeds 16 MiB");
    }
    Ok(())
}

async fn start_client(settings: StartConfig) -> Result<InProcessClientHandle> {
    let arg0_paths = Arg0DispatchPaths {
        codex_self_exe: Some(settings.codex_self_exe.clone()),
        ..Default::default()
    };
    // The app UID is the execution boundary: Linux namespace sandbox helpers cannot run under
    // Android's application SELinux domain.
    let cli_overrides = vec![
        (
            "shell_environment_policy.set".into(),
            toml::Value::Table(
                settings
                    .env
                    .into_iter()
                    .map(|(key, value)| (key, toml::Value::String(value)))
                    .collect(),
            ),
        ),
        (
            "sandbox_mode".into(),
            toml::Value::String("danger-full-access".into()),
        ),
        (
            "cli_auth_credentials_store".into(),
            toml::Value::String("file".into()),
        ),
        (
            "features.shell_snapshot".into(),
            toml::Value::Boolean(false),
        ),
        (
            "features.shell_zsh_fork".into(),
            toml::Value::Boolean(false),
        ),
    ];
    let loader_overrides = LoaderOverrides::default();
    let cloud_config_bundle = CloudConfigBundleLoader::default();
    let config = ConfigBuilder::default()
        .codex_home(settings.codex_home)
        .harness_overrides(ConfigOverrides {
            cwd: Some(settings.cwd),
            codex_self_exe: Some(settings.codex_self_exe.clone()),
            ..Default::default()
        })
        .cli_overrides(cli_overrides.clone())
        .loader_overrides(loader_overrides.clone())
        .cloud_config_bundle(cloud_config_bundle.clone())
        .build()
        .await?;
    let state_db = codex_core::init_state_db(&config).await;
    let environment_manager = EnvironmentManager::from_codex_home(
        config.codex_home.clone(),
        Some(ExecServerRuntimePaths::new(settings.codex_self_exe, None)?),
        config.http_client_factory(),
    )
    .await?;
    Ok(in_process::start(InProcessStartArgs {
        arg0_paths,
        config: Arc::new(config),
        cli_overrides,
        loader_overrides,
        strict_config: false,
        cloud_config_bundle,
        thread_config_loader: Arc::new(NoopThreadConfigLoader),
        feedback: CodexFeedback::new(),
        log_db: None,
        state_db,
        environment_manager: Arc::new(environment_manager),
        config_warnings: Vec::new(),
        // Match standalone app-server so default thread/list includes these sessions.
        session_source: SessionSource::VSCode,
        enable_codex_api_key_env: false,
        initialize: InitializeParams {
            client_info: ClientInfo {
                name: "codex_android".into(),
                title: Some("Codex Android".into()),
                version: "1.0".into(),
            },
            capabilities: Some(InitializeCapabilities {
                experimental_api: true,
                ..Default::default()
            }),
        },
        channel_capacity: QUEUE_CAPACITY,
    })
    .await?)
}

async fn run(
    mut client: InProcessClientHandle,
    mut commands: mpsc::Receiver<Command>,
    events: mpsc::Sender<Vec<u8>>,
    mut stop: oneshot::Receiver<()>,
) {
    let mut requests = JoinSet::new();
    loop {
        tokio::select! {
            biased;
            _ = &mut stop => break,
            Some(_) = requests.join_next(), if !requests.is_empty() => {},
            command = commands.recv() => {
                let Some(command) = command else { break };
                match command {
                    Command::Request(request) => {
                        let id = request.id().clone();
                        if requests.len() >= 64 {
                            send_event(&events, error_bytes(&id, -32001, "too many in-flight requests")).await;
                            continue;
                        }
                        let sender = client.sender();
                        let events = events.clone();
                        requests.spawn(async move {
                            let response = match sender.request(*request).await {
                                Ok(Ok(result)) => serde_json::to_vec(&ServerResponseEnvelope { id: &id, result: &result }),
                                Ok(Err(error)) => serde_json::to_vec(&ServerErrorEnvelope { id: &id, error: &error }),
                                Err(error) => error_bytes(&id, -32603, &error.to_string()),
                            };
                            send_event(&events, response).await;
                        });
                    }
                    Command::Notification(notification) => {
                        if let Err(error) = client.notify(*notification) {
                            send_event(&events, transport_error(&error.to_string())).await;
                        }
                    }
                    Command::ServerResponse { id, result } => {
                        if let Err(error) = client.respond_to_server_request(id, result) {
                            send_event(&events, transport_error(&error.to_string())).await;
                        }
                    }
                    Command::ServerError { id, error } => {
                        if let Err(error) = client.fail_server_request(id, error) {
                            send_event(&events, transport_error(&error.to_string())).await;
                        }
                    }
                }
            }
            event = client.next_event() => {
                let message = match event {
                    Some(InProcessServerEvent::ServerRequest(request)) => serde_json::to_vec(&*request),
                    Some(InProcessServerEvent::ServerNotification(notification)) => serde_json::to_vec(&*notification),
                    Some(InProcessServerEvent::Lagged { skipped }) => serde_json::to_vec(&LaggedNotification { method: "android/transportLagged", params: LaggedParams { skipped } }),
                    None => break,
                };
                send_event(&events, message).await;
            }
        }
    }
    requests.abort_all();
    let _ = client.shutdown().await;
}

/// Forwards one already-serialized event; a serialization error only drops that message.
async fn send_event(events: &mpsc::Sender<Vec<u8>>, message: serde_json::Result<Vec<u8>>) {
    if let Ok(message) = message {
        let _ = events.send(message).await;
    }
}

fn error_bytes(id: &RequestId, code: i64, message: &str) -> serde_json::Result<Vec<u8>> {
    serde_json::to_vec(&ServerErrorEnvelope {
        id,
        error: &JSONRPCErrorError {
            code,
            message: message.to_string(),
            data: None,
        },
    })
}

fn transport_error(message: &str) -> serde_json::Result<Vec<u8>> {
    serde_json::to_vec(&TransportErrorNotification {
        method: "android/transportError",
        params: TransportErrorParams { message },
    })
}
