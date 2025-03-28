package com.github.sarxos.webcam;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used as a global (system) lock preventing other processes from using the same
 * camera while it's open. Whenever webcam is open there is a thread running in background which
 * updates the lock once per 2 seconds. Lock is being released whenever webcam is either closed or
 * completely disposed. Lock will remain for at least 2 seconds in case when JVM has not been
 * gracefully terminated (due to SIGSEGV, SIGTERM, etc).
 *
 * @author Bartosz Firyn (sarxos)
 */
public class WebcamLock {

	private static final Logger LOG = LoggerFactory.getLogger(WebcamLock.class);
	public static final long INTERVAL = 2000;
	private static final int MAX_RETRIES = 5;
	private static final int BUFFER_SIZE = 8;

	private final Webcam webcam;
	private Thread updater = null;
	private final AtomicBoolean locked = new AtomicBoolean(false);
	private final AtomicBoolean disabled = new AtomicBoolean(false);
	private final File lock;

	protected WebcamLock(Webcam webcam) {
		this.webcam = webcam;
		this.lock = new File(System.getProperty("java.io.tmpdir"), getLockName());
		this.lock.deleteOnExit();
	}

	private String getLockName() {
		return String.format(".webcam-lock-%d", Math.abs(webcam.getName().hashCode()));
	}

	private void write(long value) {
		if (disabled.get()) {
			return;
		}

		String name = getLockName();
		File tmp = null;

		try {
			tmp = File.createTempFile(String.format("%s-tmp", name), "");
			tmp.deleteOnExit();

			try (OutputStream os = Files.newOutputStream(tmp.toPath());
				 DataOutputStream dos = new DataOutputStream(os)) {
				dos.writeLong(value);
				dos.flush();
			}

			if (!locked.get()) {
				return;
			}

			try {
				Files.move(tmp.toPath(), lock.toPath(), StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (IOException e) {
				LOG.debug("Atomic rename failed, falling back to stream copy", e);
			}

			if (!lock.exists()) {
				Files.createFile(lock.toPath());
				LOG.info("Lock file {} for {} has been created", lock, webcam);
			}

			boolean rewritten = false;
			int attempts = 0;

			synchronized (webcam) {
				do {
					try {
						Files.copy(tmp.toPath(), lock.toPath(), StandardCopyOption.REPLACE_EXISTING);
						rewritten = true;
						break;
					} catch (IOException e) {
						LOG.debug("Not able to rewrite lock file (attempt {})", attempts + 1, e);
					}
				} while (attempts++ < MAX_RETRIES);
			}

			if (!rewritten) {
				throw new WebcamException("Not able to write lock file");
			}

		} catch (IOException e) {
			throw new RuntimeException("Error handling lock file", e);
		} finally {
			if (tmp != null && !tmp.delete()) {
				tmp.deleteOnExit();
			}
		}
	}

	private long read() {
		if (disabled.get()) {
			return -1;
		}

		long value = -1;
		boolean broken = false;

		synchronized (webcam) {
			try (InputStream is = Files.newInputStream(lock.toPath());
				 DataInputStream dis = new DataInputStream(is)) {
				value = dis.readLong();
			} catch (EOFException e) {
				LOG.debug("Webcam lock is broken - EOF when reading long variable from stream", e);
				broken = true;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			if (broken) {
				LOG.warn("Lock file {} for {} is broken - recreating it", lock, webcam);
				write(-1);
			}
		}

		return value;
	}

	// ... [Rest of the class remains unchanged: LockUpdater, update(), lock(), disable(), unlock(), isLocked(), getLockFile()]
}