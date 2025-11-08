package proyecto_chat.client;

import javax.sound.sampled.*;
import java.io.*;

public class AudioRecorder {
    /**
     * Graba un WAV PCM (44100 Hz, 16 bits, mono) durante durationSeconds y devuelve el File temporal.
     */
    public static File recordWav(int durationSeconds) throws Exception {
        AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new IllegalStateException("Línea de audio no soportada en este sistema.");
        }

        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        File tempFile = File.createTempFile("recording_", ".wav");
        tempFile.deleteOnExit();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int bufferSize = (int) format.getSampleRate() * format.getFrameSize() / 4; // chunk razonable
            byte[] buffer = new byte[bufferSize];
            long end = System.currentTimeMillis() + (durationSeconds * 1000L);
            while (System.currentTimeMillis() < end) {
                int count = line.read(buffer, 0, buffer.length);
                if (count > 0) baos.write(buffer, 0, count);
            }
            byte[] audioData = baos.toByteArray();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                 AudioInputStream ais = new AudioInputStream(bais, format, audioData.length / format.getFrameSize())) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempFile);
            }
        } finally {
            line.stop();
            line.close();
        }

        return tempFile;
    }
}
