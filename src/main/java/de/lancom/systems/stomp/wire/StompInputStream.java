package de.lancom.systems.stomp.wire;

import static de.lancom.systems.stomp.util.StringUtil.isEmpty;

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import de.lancom.systems.stomp.util.CountDown;
import de.lancom.systems.stomp.wire.frame.Frame;
import lombok.extern.slf4j.Slf4j;

/**
 * Input stream for stomp {@link Frame}.
 */
@Slf4j
public class StompInputStream extends InputStream {
    private final ReentrantLock lock = new ReentrantLock();
    private final StompContext context;
    private final InputStream inputStream;
    private final BufferedReader reader;

    /**
     * Default constructor.
     *
     * @param context stomp context
     * @param inputStream input stream
     */
    public StompInputStream(final StompContext context, final InputStream inputStream) {
        this.context = context;
        this.inputStream = inputStream;
        this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    /**
     * Read frame from underlying input stream.
     *
     * @return frame or null if none is available
     * @throws IOException if an I/O error occurs
     */
    public Frame readFrame() throws IOException {
        return readFrame(0, TimeUnit.MILLISECONDS);
    }

    /**
     * Read frame from underlying input stream.
     *
     * @param timeout timout
     * @param unit time unit
     * @return frame or null if none was available in the given time
     * @throws IOException if an I/O error occurs
     */
    public Frame readFrame(final long timeout, final TimeUnit unit) throws IOException {
        final CountDown countDown = new CountDown(timeout, unit);

        Frame frame = null;
        boolean locked;
        try {
            locked = lock.tryLock(countDown.remaining(), TimeUnit.MILLISECONDS);
            if (locked) {
                // read action
                do {
                    while (reader.ready()) {
                        final String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        if (isEmpty(line)) {
                            continue;
                        } else {
                            frame = context.createFrame(line);
                            break;
                        }
                    }
                } while (countDown.remaining() > 0 && frame == null);

                if (frame != null) {

                    // read headers
                    while (true) {
                        final String line = reader.readLine();
                        if (isEmpty(line)) {
                            break;
                        }

                        final String[] parts = line.split(":", 2);
                        if (parts.length == 2) {
                            final String headerName = parts[0];
                            final String headerValue;
                            if (Objects.equals(frame.getAction(), StompAction.CONNECT.value())) {
                                headerValue = parts[1];
                            } else {
                                headerValue = StompEncoding.decodeHeaderValue(parts[1]);
                            }

                            frame.getHeaders().put(headerName, headerValue);
                        }
                    }

                    // read body
                    final Integer contentLength = frame.getContentLength();
                    final CharArrayWriter writer = new CharArrayWriter();
                    while (reader.ready()) {
                        final int value = reader.read();

                        final boolean more;
                        if (contentLength != null) {
                            more = contentLength > writer.size();
                        } else {
                            more = value != 0;
                        }
                        if (more) {
                            writer.write(value);
                        } else {
                            break;
                        }
                    }

                    final char[] body = writer.toCharArray();
                    if (body != null && body.length > 0) {
                        frame.setBodyAsString(new String(body));
                    }
                }
            }
        } catch (final InterruptedException ex) {
            if (log.isErrorEnabled()) {
                log.error("Error reading messages", ex);
            }
        } finally {
            lock.unlock();
        }

        return frame;
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return inputStream.read(b);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return inputStream.read(b, off, len);
    }

    @Override
    public long skip(final long n) throws IOException {
        return inputStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    @Override
    public synchronized void mark(final int readlimit) {
        inputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        inputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return inputStream.markSupported();
    }
}