package com.willhauck.linconnectclient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class ImageUtilities {

	public static InputStream bitmapToInputStream(Bitmap bitmap) {
		int size = bitmap.getHeight() * bitmap.getRowBytes();
		ByteBuffer buffer = ByteBuffer.allocate(size);
		bitmap.copyPixelsToBuffer(buffer);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		// Compress as PNG @ 50% quality for tiny size
		bitmap.compress(Bitmap.CompressFormat.PNG, 50, bos);
		return new ByteArrayInputStream(bos.toByteArray());
	}

	public static Bitmap drawableToBitmap(Drawable drawable) {
		if (drawable instanceof BitmapDrawable) {
			return ((BitmapDrawable) drawable).getBitmap();
		}

		Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
				drawable.getIntrinsicHeight(), Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
		drawable.draw(canvas);

		return bitmap;
	}

}
