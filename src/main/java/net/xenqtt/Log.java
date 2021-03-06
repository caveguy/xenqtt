/**
    Copyright 2013 James McClure

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package net.xenqtt;

/**
 * <p>
 * Provides disparate logging methods for use within xenqtt. The {@code Log} class is able to detect other logging implementations and, should they be present
 * and available, use those by default. Should other logging implementations not be available the fallback logging mechanism will be used.
 * </p>
 * 
 * <p>
 * At present the fallback logging mechanism writes all {@code trace}, {@code debug}, and {@code info} logging events to standard out and all {@code warn},
 * {@code error}, and {@code fatal} logging events to standard error.
 * </p>
 */
public final class Log {

	private static LogDelegate DELEGATE;

	static {
		try {
			DELEGATE = new Log4jLogDelegate();
		} catch (NoClassDefFoundError ignore) {
			DELEGATE = new NullLogDelegate();
		}
	}

	private Log() {
	}

	/**
	 * Log a message at the TRACE log level, if possible.
	 * 
	 * @param message
	 *            The log message to emit
	 * @param parameters
	 *            The parameters to use in replacing format specifiers in the specified {@code message}. This can be omitted if no such specifiers exist
	 */
	public static void trace(String message, Object... parameters) {
		DELEGATE.trace(message, parameters);
	}

	/**
	 * Log a message at the DEBUG log level, if possible.
	 * 
	 * @param message
	 *            The log message to emit
	 * @param parameters
	 *            The parameters to use in replacing format specifiers in the specified {@code message}. This can be omitted if no such specifiers exist
	 */
	public static void debug(String message, Object... parameters) {
		DELEGATE.debug(message, parameters);
	}

	/**
	 * Log a message at the INFO log level, if possible.
	 * 
	 * @param message
	 *            The log message to emit
	 * @param parameters
	 *            The parameters to use in replacing format specifiers in the specified {@code message}. This can be omitted if no such specifiers exist
	 */
	public static void info(String message, Object... parameters) {
		DELEGATE.info(message, parameters);
	}

	/**
	 * Log a message at the WARN log level, if possible.
	 * 
	 * @param message
	 *            The log message to emit
	 * @param parameters
	 *            The parameters to use in replacing format specifiers in the specified {@code message}. This can be omitted if no such specifiers exist
	 */
	public static void warn(String message, Object... parameters) {
		DELEGATE.warn(message, parameters);
	}

	/**
	 * Log a message at the WARN log level, if possible.
	 * 
	 * @param t
	 *            A {@link Throwable} that was thrown at some point that is being logged with the {@code message} and {@code parameters}
	 * @param message
	 *            The log message to emit
	 * @param parameters
	 *            The parameters to use in replacing format specifiers in the specified {@code message}. This can be omitted if no such specifiers exist
	 */
	public static void warn(Throwable t, String message, Object... parameters) {
		DELEGATE.warn(t, message, parameters);
	}

	/**
	 * Log a message at the ERROR log level, if possible.
	 * 
	 * @param message
	 *            The log message to emit
	 * @param parameters
	 *            The parameters to use in replacing format specifiers in the specified {@code message}. This can be omitted if no such specifiers exist
	 */
	public static void error(String message, Object... parameters) {
		DELEGATE.error(message, parameters);
	}

	/**
	 * Log a message at the ERROR log level, if possible.
	 * 
	 * @param t
	 *            A {@link Throwable} that was thrown at some point that is being logged with the {@code message} and {@code parameters}
	 * @param message
	 *            The log message to emit
	 * @param parameters
	 *            The parameters to use in replacing format specifiers in the specified {@code message}. This can be omitted if no such specifiers exist
	 */
	public static void error(Throwable t, String message, Object... parameters) {
		DELEGATE.error(t, message, parameters);
	}

	/**
	 * Log a message at the FATAL log level.
	 * 
	 * @param message
	 *            The log message to emit
	 * @param parameters
	 *            The parameters to use in replacing format specifiers in the specified {@code message}. This can be omitted if no such specifiers exist
	 */
	public static void fatal(String message, Object... parameters) {
		DELEGATE.fatal(message, parameters);
	}

	/**
	 * Log a message at the FATAL log level.
	 * 
	 * @param t
	 *            A {@link Throwable} that was thrown at some point that is being logged with the {@code message} and {@code parameters}
	 * @param message
	 *            The log message to emit
	 * @param parameters
	 *            The parameters to use in replacing format specifiers in the specified {@code message}. This can be omitted if no such specifiers exist
	 */
	public static void fatal(Throwable t, String message, Object... parameters) {
		DELEGATE.fatal(t, message, parameters);
	}

}
