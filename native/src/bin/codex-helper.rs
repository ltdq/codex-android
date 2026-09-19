fn main() {
    #[cfg(target_os = "android")]
    {
        let shell = std::env::var_os("CODEX_SHELL")
            .map(std::path::PathBuf::from)
            .expect("Android helper requires CODEX_SHELL from the packaged toolchain");
        codex_shell_command::android::configure(shell)
            .expect("Android helper requires a valid packaged Bash executable");
    }
    let _guard = codex_arg0::arg0_dispatch();
    eprintln!("Codex Android helper must be invoked with a supported helper command");
    std::process::exit(2);
}
