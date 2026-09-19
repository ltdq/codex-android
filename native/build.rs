fn main() {
    println!("cargo:rerun-if-env-changed=CODEX_ANDROID_BUILTINS");
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("android") {
        let builtins = std::env::var("CODEX_ANDROID_BUILTINS")
            .expect("build Android through native/build.sh to configure the NDK runtime");
        assert!(
            std::path::Path::new(&builtins).is_file(),
            "NDK builtins archive is missing"
        );
        // C dependencies use outlined ARM atomics provided by compiler-rt.
        println!("cargo:rustc-link-arg={builtins}");
        println!("cargo:rustc-link-arg=-Wl,--no-undefined");
    }
}
