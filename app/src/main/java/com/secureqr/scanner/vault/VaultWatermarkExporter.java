package com.secureqr.scanner.vault;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;

public final class VaultWatermarkExporter {
    public static File create(Context context,File source,String mime,String text)throws Exception{
        return create(context,source,mime,text,Color.rgb(25,90,180),22,100);
    }
    public static File create(Context context,File source,String mime,String text,int color,int opacity,int sizePercent)throws Exception{
        if(mime!=null&&mime.startsWith("image/"))return image(context,source,text,color,opacity,sizePercent);
        if("application/pdf".equals(mime))return pdf(context,source,text,color,opacity,sizePercent);
        return source;
    }
    private static File image(Context c,File source,String text,int color,int opacity,int size)throws Exception{Bitmap original=BitmapFactory.decodeFile(source.getAbsolutePath());if(original==null)throw new IllegalArgumentException("Unsupported image");Bitmap out=original.copy(Bitmap.Config.ARGB_8888,true);if(out==null){original.recycle();throw new IllegalStateException("Unable to create watermark bitmap");}draw(new Canvas(out),out.getWidth(),out.getHeight(),text,color,opacity,size);String base=source.getName().replaceFirst("(?i)\\.[a-z0-9]+$","");File f=target(c,base+"_watermarked.png");try(FileOutputStream s=new FileOutputStream(f)){if(!out.compress(Bitmap.CompressFormat.PNG,100,s))throw new IllegalStateException("Unable to encode watermark image");s.flush();}if(out!=original)out.recycle();original.recycle();if(!f.isFile()||f.length()==0)throw new IllegalStateException("Watermark image is empty");return f;}
    private static File pdf(Context c,File source,String text,int color,int opacity,int size)throws Exception{File out=target(c,source.getName()+"_watermarked.pdf");PdfDocument doc=new PdfDocument();try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){for(int n=0;n<renderer.getPageCount();n++){PdfRenderer.Page page=renderer.openPage(n);int w=page.getWidth(),h=page.getHeight();PdfDocument.Page p=doc.startPage(new PdfDocument.PageInfo.Builder(w,h,n+1).create());Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);p.getCanvas().drawBitmap(b,0,0,null);draw(p.getCanvas(),w,h,text,color,opacity,size);doc.finishPage(p);b.recycle();page.close();}try(FileOutputStream stream=new FileOutputStream(out)){doc.writeTo(stream);}}finally{doc.close();}return out;}
    private static void draw(Canvas canvas,int width,int height,String text,int color,int opacity,int sizePercent){Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.argb(Math.max(0,Math.min(255,opacity*255/100)),Color.red(color),Color.green(color),Color.blue(color)));paint.setTextSize(Math.max(18,width/24f*Math.max(50,sizePercent)/100f));paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);canvas.save();canvas.rotate(-28,width/2f,height/2f);float xGap=Math.max(220,width/2.2f),yGap=Math.max(130,height/5f);String line=text==null||text.trim().isEmpty()?"KeyScan":text.replace('\n',' ');for(float y=-height;y<height*2;y+=yGap)for(float x=-width;x<width*2;x+=xGap)canvas.drawText(line,x,y,paint);canvas.restore();}
    private static File target(Context c,String name){File dir=new File(c.getCacheDir(),"vault_exports");if(!dir.exists())dir.mkdirs();return new File(dir,name.replace("/","_").replace("\\","_"));}
    private VaultWatermarkExporter(){}
}
