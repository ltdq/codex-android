use std::fs::{File, OpenOptions, TryLockError};
use std::io;
use std::path::Path;

fn open(path: &Path) -> io::Result<File> {
    OpenOptions::new()
        .create(true)
        .truncate(false)
        .read(true)
        .write(true)
        .open(path)
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let path = std::env::args()
        .nth(1)
        .ok_or("usage: file-lock-probe LOCK_PATH")?;
    let path = Path::new(&path);
    let first = open(path)?;
    let second = open(path)?;
    let third = open(path)?;

    first.lock()?;
    assert!(matches!(second.try_lock(), Err(TryLockError::WouldBlock)));
    assert!(matches!(
        second.try_lock_shared(),
        Err(TryLockError::WouldBlock)
    ));
    first.unlock()?;
    second.try_lock()?;
    second.unlock()?;
    println!("PASS exclusive lock contention and unlock/reacquire");

    first.lock_shared()?;
    second.try_lock_shared()?;
    assert!(matches!(third.try_lock(), Err(TryLockError::WouldBlock)));
    first.unlock()?;
    assert!(matches!(third.try_lock(), Err(TryLockError::WouldBlock)));
    second.unlock()?;
    third.try_lock()?;
    third.unlock()?;
    println!("PASS shared lock coexistence and exclusive exclusion");

    first.lock()?;
    drop(first);
    second.try_lock()?;
    drop(second);
    third.try_lock()?;
    third.unlock()?;
    println!("PASS close releases the file lock");
    std::fs::remove_file(path)?;
    Ok(())
}
