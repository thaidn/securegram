package xyz.securegram;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.Result;
import com.google.zxing.BinaryBitmap;

import com.google.zxing.common.HybridBinarizer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Vector;

public class QrCode {
  private static final String TAG = QrCode.class.getName();
  private static final int WHITE = 0xFFFFFFFF;
  private static final int BLACK = 0xFF000000;

  private static BarcodeFormat FORMAT = BarcodeFormat.QR_CODE;
  private static String ENCODING = "UTF-8";


  /**
   *  Encodes and returns the QrCodeEncoder object as a bitmap suitable for display.
   */
  public static Bitmap encodeAsBitmap(String content, int dimension) throws WriterException {
    Map<EncodeHintType, Object> hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
    hints.put(EncodeHintType.CHARACTER_SET, ENCODING);

    MultiFormatWriter writer = new MultiFormatWriter();
    BitMatrix result = writer.encode(content, FORMAT, dimension, dimension, hints);
    int width = result.getWidth();
    int height = result.getHeight();
    int[] pixels = new int[width * height];

    // All are 0, or black, by default
    for (int y = 0; y < height; y++) {
      int offset = y * width;
      for (int x = 0; x < width; x++) {
        pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
      }
    }

    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    return bitmap;
  }

  public static String decodeAsString(String filePath) {
    try {
      FileInputStream is = new FileInputStream(filePath);
      Bitmap bitmap = BitmapFactory.decodeStream(is);
      if (bitmap == null) {
        Log.e(TAG, "filePath is not a bitmap," + filePath);
        return null;
      }
      int width = bitmap.getWidth(), height = bitmap.getHeight();
      int[] pixels = new int[width * height];
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
      bitmap.recycle();
      bitmap = null;
      RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
      BinaryBitmap bBitmap = new BinaryBitmap(new HybridBinarizer(source));
      Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
      hints.put(DecodeHintType.CHARACTER_SET, ENCODING);
      Vector<BarcodeFormat> formats = new Vector<BarcodeFormat>();
      formats.add(BarcodeFormat.QR_CODE);
      hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
      MultiFormatReader reader = new MultiFormatReader();
      reader.setHints(hints);
      try {
        Result result = reader.decode(bBitmap);
        return result.getText();
      } catch (NotFoundException e) {
        Log.e(TAG, "decode exception", e);
        return null;
      } finally {
        reader.reset();
      }
    } catch (FileNotFoundException e) {
      Log.e(TAG, "can not open file" + filePath, e);
      return null;
    }
  }
}
