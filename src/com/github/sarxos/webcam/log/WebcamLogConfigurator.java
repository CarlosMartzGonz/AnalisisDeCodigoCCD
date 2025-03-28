package com.github.sarxos.webcam.log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configure loggers.
 *
 * @author Bartosz Firyn (SarXos)
 */
public class WebcamLogConfigurator {

	private static final Logger LOG = LoggerFactory.getLogger(WebcamLogConfigurator.class);

	/**
	 * Configure SLF4J.
	 *
	 * @param is input stream to logback configuration xml
	 */
	public static void configure(InputStream is) {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();

		try {
			String[] names = {
					"ch.qos.logback.classic.LoggerContext",
					"ch.qos.logback.classic.joran.JoranConfigurator",
			};
			for (String name : names) {
				Class.forName(name, false, cl);
			}

			ch.qos.logback.classic.LoggerContext context = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
			ch.qos.logback.classic.joran.JoranConfigurator configurator = new ch.qos.logback.classic.joran.JoranConfigurator();
			configurator.setContext(context);
			context.reset();
			configurator.doConfigure(is);

		} catch (ClassNotFoundException e) {
			System.err.println("WLogC: Logback JARs are missing in classpath");
		} catch (NoClassDefFoundError e) {
			System.err.println("WLogC: Logback JARs are missing in classpath");
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	/**
	 * Configure SLF4J.
	 *
	 * @param file logback configuration file
	 */
	public static void configure(File file) {
		Path path = file.toPath();
		try (InputStream is = Files.newInputStream(path)) {
			configure(is);
		} catch (IOException e) {
			LOG.error("Error processing file " + file, e);
			e.printStackTrace();
		}
	}

	/**
	 * Configure SLF4J.
	 *
	 * @param filePath logback configuration file path
	 */
	public static void configure(String filePath) {
		Path path = Paths.get(filePath);
		try (InputStream is = Files.newInputStream(path)) {
			configure(is);
		} catch (IOException e) {
			LOG.error("Error processing file " + filePath, e);
			e.printStackTrace();
		}
	}
}