package medley.utils;

import org.apache.avro.io.*;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Utility class for serializing and de-serializing avro objects.
 */
public final class AvroUtils {

  private AvroUtils() {
  }

  public static <T> byte[] serialize(final T obj, final Class<T> targetClass) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
    final DatumWriter<T> writer = new SpecificDatumWriter<>(targetClass);

    writer.write(obj, encoder);
    encoder.flush();
    out.close();
    return out.toByteArray();
  }

  public static <T> T deserialize(final byte[] bytes, final Class<T> targetClass) throws IOException {
    final SpecificDatumReader<T> reader = new SpecificDatumReader<>(targetClass);
    final Decoder decoder = DecoderFactory.get().binaryDecoder(bytes, null);
    return reader.read(null, decoder);
  }
}
