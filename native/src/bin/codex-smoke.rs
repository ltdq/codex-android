use anyhow::{Result, anyhow, bail, ensure};
use codex_android_jni::Bridge;
use codex_utils_path_uri::PathUri;
use serde_json::{Value, json};
use std::io::Write;
use std::path::PathBuf;
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

fn request(bridge: &Bridge, id: i64, method: &str, params: Value) -> Result<Value> {
    bridge.send_request(&serde_json::to_vec(&json!({"id":id,"method":method,"params":params}))?)?;
    let deadline = Instant::now() + Duration::from_secs(60);
    loop {
        ensure!(Instant::now() < deadline, "timed out waiting for {method}");
        let Some(message) = bridge.receive(Duration::from_secs(1))? else {
            continue;
        };
        let message: Value = serde_json::from_slice(&message)?;
        if message.get("id") == Some(&json!(id)) {
            if let Some(error) = message.get("error") {
                bail!("{method}: {error}");
            }
            println!("PASS {method}");
            return message
                .get("result")
                .cloned()
                .ok_or_else(|| anyhow!("missing result"));
        }
        if message.get("id").is_some() && message.get("method").is_some() {
            bail!("unexpected server request: {message}");
        }
    }
}

fn shell_turn(bridge: &Bridge, thread_id: &str) -> Result<()> {
    bridge.send_request(&serde_json::to_vec(&json!({"id":9,"method":"thread/shellCommand","params":{
        "threadId":thread_id,"command":"printf 'native-shell-ok:%s\\n' \"${BASH_VERSION:-missing}\"; command -v git","timeoutMs":10000
    }}))?)?;
    let deadline = Instant::now() + Duration::from_secs(60);
    let mut acknowledged = false;
    let mut completed = false;
    let mut output = String::new();
    while !acknowledged || !completed {
        ensure!(Instant::now() < deadline, "shell turn timed out");
        let Some(message) = bridge.receive(Duration::from_secs(1))? else {
            continue;
        };
        let message: Value = serde_json::from_slice(&message)?;
        if message.get("id") == Some(&json!(9)) {
            ensure!(
                message.get("error").is_none(),
                "shell turn failed: {message}"
            );
            acknowledged = true;
        }
        let params = &message["params"];
        if params["threadId"] != thread_id {
            continue;
        }
        match message["method"].as_str() {
            Some("item/commandExecution/outputDelta") => {
                output.push_str(params["delta"].as_str().unwrap_or_default());
            }
            Some("item/completed") if params["item"]["type"] == "commandExecution" => {
                if let Some(aggregated) = params["item"]["aggregatedOutput"].as_str() {
                    output = aggregated.to_string();
                }
            }
            Some("turn/completed") => {
                ensure!(
                    params["turn"]["status"] == "completed",
                    "shell turn did not complete: {message}"
                );
                completed = true;
            }
            _ => {}
        }
    }
    ensure!(
        output.contains("native-shell-ok:"),
        "shell output missing: {output}"
    );
    ensure!(
        output.contains("/git"),
        "git not found in injected PATH: {output}"
    );
    #[cfg(target_os = "android")]
    ensure!(
        !output.contains("native-shell-ok:missing"),
        "thread did not run packaged Bash"
    );
    println!("PASS thread/shellCommand: {output}");
    Ok(())
}

fn assert_shell_history(response: &Value, thread_id: &str) -> Result<()> {
    ensure!(response["thread"]["id"] == thread_id, "thread ID changed");
    ensure!(
        response["thread"]["turns"]
            .as_array()
            .is_some_and(|turns| turns.iter().any(|turn| {
                turn["items"].as_array().is_some_and(|items| {
                    items.iter().any(|item| {
                        item["type"] == "commandExecution"
                            && item["aggregatedOutput"]
                                .as_str()
                                .is_some_and(|output| output.contains("native-shell-ok:"))
                    })
                })
            })),
        "shell command history missing: {response}"
    );
    Ok(())
}

fn main() -> Result<()> {
    let scratch = PathBuf::from(
        std::env::args()
            .nth(1)
            .ok_or_else(|| anyhow!("usage: codex-smoke SCRATCH_DIR CODEX_HELPER"))?,
    );
    let helper = PathBuf::from(
        std::env::args()
            .nth(2)
            .ok_or_else(|| anyhow!("missing helper executable"))?,
    );
    ensure!(!scratch.exists(), "use a new scratch directory");
    let config = json!({
        "codexHome":scratch.join("codex"),
        "cwd":scratch.join("workspace"),
        "codexSelfExe":helper,
        "env":{}
    });
    #[cfg(target_os = "android")]
    let config = {
        let mut config = config;
        let shell = PathBuf::from(std::env::var("CODEX_SHELL")?);
        let toolchain = shell
            .parent()
            .and_then(std::path::Path::parent)
            .ok_or_else(|| anyhow!("CODEX_SHELL must identify TOOLCHAIN/bin/bash"))?;
        config["toolchainRoot"] = json!(toolchain);
        config["shellPath"] = json!(shell);
        let environment: std::collections::BTreeMap<_, _> = std::env::vars().collect();
        config["env"] = json!(environment);
        config
    };
    let bridge = Bridge::start(&serde_json::to_vec(&config)?)?;
    println!("PASS initialize/initialized");
    let models = request(&bridge, 1, "model/list", json!({}))?;
    ensure!(
        models["data"]
            .as_array()
            .is_some_and(|models| !models.is_empty()),
        "model catalog is empty"
    );
    let threads = request(&bridge, 7, "thread/list", json!({}))?;
    ensure!(threads["data"].is_array(), "thread list is not an array");
    request(&bridge, 8, "account/read", json!({"refreshToken":false}))?;
    let thread = request(
        &bridge,
        2,
        "thread/start",
        json!({
            "cwd":scratch.join("workspace"),"sandbox":"danger-full-access","approvalPolicy":"on-request"
        }),
    )?;
    let thread_id = thread["thread"]["id"]
        .as_str()
        .ok_or_else(|| anyhow!("missing thread id"))?;
    shell_turn(&bridge, thread_id)?;
    let threads = request(&bridge, 10, "thread/list", json!({}))?;
    ensure!(threads["data"].is_array(), "thread list is not an array");
    let shell = codex_core::shell::default_user_shell();
    #[cfg(target_os = "android")]
    ensure!(shell.name() == "bash", "core did not select packaged Bash");
    let command = shell.derive_exec_args("printf codex-native-ok", true);
    let output = request(
        &bridge,
        3,
        "command/exec",
        json!({
            "command":command,
            "cwd":scratch.join("workspace"),"sandboxPolicy":{"type":"dangerFullAccess"},"timeoutMs":10000
        }),
    )?;
    ensure!(output["exitCode"] == 0, "command failed: {output}");
    ensure!(
        output["stdout"] == "codex-native-ok",
        "unexpected command output: {output}"
    );
    let history = request(
        &bridge,
        4,
        "thread/read",
        json!({"threadId":thread_id,"includeTurns":true}),
    )?;
    assert_shell_history(&history, thread_id)?;
    bridge.close()?;
    bridge.close()?;
    ensure!(
        bridge.send_request(b"{}").is_err(),
        "closed bridge accepted a message"
    );
    drop(bridge);
    println!("PASS shutdown");
    let bridge = Bridge::start(&serde_json::to_vec(&config)?)?;
    request(&bridge, 6, "model/list", json!({}))?;
    // Upstream deliberately omits shell-only threads with empty previews from list.
    let history = request(
        &bridge,
        12,
        "thread/read",
        json!({"threadId":thread_id,"includeTurns":true}),
    )?;
    assert_shell_history(&history, thread_id)?;
    let resumed = request(&bridge, 13, "thread/resume", json!({"threadId":thread_id}))?;
    assert_shell_history(&resumed, thread_id)?;
    request(&bridge, 5, "thread/archive", json!({"threadId":thread_id}))?;
    bridge.close()?;
    drop(bridge);
    println!("PASS restart");
    let helper = config["codexSelfExe"]
        .as_str()
        .ok_or_else(|| anyhow!("missing helper"))?;
    let patch = "*** Begin Patch\n*** Add File: native-smoke.txt\n+native helper ok\n*** End Patch";
    let output = Command::new(helper)
        .current_dir(scratch.join("workspace"))
        .args(["--codex-run-as-apply-patch", patch])
        .output()?;
    ensure!(
        output.status.success(),
        "apply_patch helper failed: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    ensure!(
        std::fs::read_to_string(scratch.join("workspace/native-smoke.txt"))?
            == "native helper ok\n"
    );
    println!("PASS apply_patch helper");
    let mut child = Command::new(helper)
        .arg("--codex-run-as-fs-helper")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .spawn()?;
    let path = PathUri::from_host_native_path(scratch.join("workspace/native-smoke.txt"))?;
    writeln!(
        child
            .stdin
            .take()
            .ok_or_else(|| anyhow!("missing helper stdin"))?,
        "{}",
        json!({"operation":"fs/readFile","params":{"path":path}})
    )?;
    let output = child.wait_with_output()?;
    ensure!(output.status.success(), "filesystem helper failed");
    let response: Value = serde_json::from_slice(&output.stdout)?;
    ensure!(
        response["status"] == "ok",
        "filesystem helper error: {response}"
    );
    ensure!(response["payload"]["response"]["dataBase64"] == "bmF0aXZlIGhlbHBlciBvawo=");
    println!("PASS filesystem helper");
    let command = shell.derive_exec_args("printf %s \"$0\"", false);
    let output = Command::new(helper)
        .args(["--codex-run-as-arg0-exec-helper", "codex-test-shell"])
        .args(command)
        .output()?;
    ensure!(
        output.status.success() && output.stdout == b"codex-test-shell",
        "arg0 exec helper failed: status={}, stdout={}, stderr={}",
        output.status,
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    println!("PASS arg0 exec helper");
    Ok(())
}
