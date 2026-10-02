//! The log of a node running as a service where the system keeps no journal
//! of its own (macOS, Windows): a file that never grows past [`MAX_SIZE`],
//! with the previous one kept beside it as `<name>.1`.

use std::{
    fs::{self, File, OpenOptions},
    io::{self, Write},
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
};

use tracing_subscriber::fmt::MakeWriter;

const MAX_SIZE: u64 = 1 << 20;

#[derive(Clone)]
pub struct LogFile(Arc<Mutex<Open>>);

struct Open {
    path: PathBuf,
    file: Option<File>,
    size: u64,
}

impl LogFile {
    pub fn open(path: &Path) -> io::Result<Self> {
        let file = open(path, false)?;
        let size = file.metadata()?.len();
        Ok(Self(Arc::new(Mutex::new(Open {
            path: path.to_owned(),
            file: Some(file),
            size,
        }))))
    }
}

impl Open {
    fn write(&mut self, buf: &[u8]) -> io::Result<()> {
        if self.size > 0 && self.size + buf.len() as u64 > MAX_SIZE {
            // Closed first: Windows renames no open file.
            self.file = None;
            let rotated = fs::rename(&self.path, self.path.with_extension("log.1"));
            // Should something hold the old one, the log starts over instead.
            self.file = Some(open(&self.path, rotated.is_err())?);
            self.size = 0;
        }
        let file = match self.file.take() {
            Some(file) => file,
            None => open(&self.path, false)?,
        };
        self.file.insert(file).write_all(buf)?;
        self.size += buf.len() as u64;
        Ok(())
    }
}

fn open(path: &Path, truncate: bool) -> io::Result<File> {
    let mut options = OpenOptions::new();
    options.create(true);
    if truncate {
        options.write(true).truncate(true);
    } else {
        options.append(true);
    }
    #[cfg(unix)]
    std::os::unix::fs::OpenOptionsExt::mode(&mut options, 0o600);
    options.open(path)
}

pub struct Writer(LogFile);

impl Write for Writer {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        let mut open = self
            .0
            .0
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        open.write(buf)?;
        Ok(buf.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

impl<'a> MakeWriter<'a> for LogFile {
    type Writer = Writer;

    fn make_writer(&'a self) -> Self::Writer {
        Writer(self.clone())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_log_rotates_and_keeps_one_old_file() {
        let dir = std::env::temp_dir().join(format!("halo-log-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        let path = dir.join("halo.log");
        let log = LogFile::open(&path).unwrap();
        let line = vec![b'x'; 1000];
        for _ in 0..2500 {
            log.make_writer().write_all(&line).unwrap();
        }
        let current = fs::metadata(&path).unwrap().len();
        let old = fs::metadata(dir.join("halo.log.1")).unwrap().len();
        assert!(current <= MAX_SIZE && old <= MAX_SIZE, "{current} {old}");
        assert!(current > 0 && old > MAX_SIZE - 1000);
        // Appends to what is there after a restart.
        drop(log);
        LogFile::open(&path)
            .unwrap()
            .make_writer()
            .write_all(&line)
            .unwrap();
        assert_eq!(fs::metadata(&path).unwrap().len(), current + 1000);
        fs::remove_dir_all(&dir).unwrap();
    }
}
