package com.example.selfiememory.service

import android.content.*
import android.graphics.*
import android.media.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.selfiememory.domain.model.Selfie
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class MemoryExporter @Inject constructor(@ApplicationContext private val context:Context){
    fun collage(a:Selfie,b:Selfie):Uri{
        val width=1080;val half=720;val output=Bitmap.createBitmap(width,half,Bitmap.Config.ARGB_8888);val canvas=Canvas(output);canvas.drawColor(Color.BLACK)
        drawFit(canvas,load(a),Rect(0,0,width/2,half));drawFit(canvas,load(b),Rect(width/2,0,width,half))
        val uri=createImage("vergleich_${stamp()}.jpg");context.contentResolver.openOutputStream(uri,"w")!!.use{output.compress(Bitmap.CompressFormat.JPEG,92,it)};finish(uri);output.recycle();return uri
    }

    fun monthlyVideo(items:List<Selfie>):Uri{
        require(items.isNotEmpty()){ "Keine Fotos im Zeitraum" }
        val chosen=items.groupBy{SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(Date(it.timestamp))}.values.map{it.firstOrNull(Selfie::favorite)?:it.first()}.take(31).reversed()
        val w=360;val h=640;val format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,w,h).apply{setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);setInteger(MediaFormat.KEY_BIT_RATE,700_000);setInteger(MediaFormat.KEY_FRAME_RATE,1);setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)}
        val codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start()
        val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,"selfie_memory_${stamp()}.mp4");put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");if(Build.VERSION.SDK_INT>=29){put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/Selfie Memory");put(MediaStore.Video.Media.IS_PENDING,1)}}
        val uri=context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)?:error("Video konnte nicht angelegt werden")
        try{context.contentResolver.openFileDescriptor(uri,"rw")!!.use{pfd->val muxer=MediaMuxer(pfd.fileDescriptor,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);var track=-1;var started=false;val info=MediaCodec.BufferInfo();var frame=0
            fun drain(end:Boolean){while(true){val index=codec.dequeueOutputBuffer(info,if(end)10_000 else 0);when{index==MediaCodec.INFO_TRY_AGAIN_LATER->if(end)continue else return;index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED->{track=muxer.addTrack(codec.outputFormat);muxer.start();started=true};index>=0->{codec.getOutputBuffer(index)?.let{buf->if(info.size>0&&started){buf.position(info.offset);buf.limit(info.offset+info.size);muxer.writeSampleData(track,buf,info)}};codec.releaseOutputBuffer(index,false);if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)return}}}}
            chosen.forEach{selfie->repeat(2){val input=codec.dequeueInputBuffer(10_000);if(input>=0){val image=renderFrame(load(selfie),w,h,selfie.timestamp);val yuv=toI420(image);image.recycle();codec.getInputBuffer(input)!!.apply{clear();put(yuv)};codec.queueInputBuffer(input,0,yuv.size,frame*1_000_000L/1,0);frame++};drain(false)}}
            val last=codec.dequeueInputBuffer(10_000);if(last>=0)codec.queueInputBuffer(last,0,0,frame*1_000_000L,MediaCodec.BUFFER_FLAG_END_OF_STREAM);drain(true);if(started)muxer.stop();muxer.release()}
            finish(uri);return uri
        }catch(t:Throwable){context.contentResolver.delete(uri,null,null);throw t}finally{runCatching{codec.stop()};codec.release()}
    }
    private fun load(s:Selfie):Bitmap{val stream=s.mediaUri?.let{context.contentResolver.openInputStream(Uri.parse(it))}?:File(s.filePath).inputStream();return stream.use{BitmapFactory.decodeStream(it)}?:error("Foto nicht lesbar")}
    private fun renderFrame(src:Bitmap,w:Int,h:Int,time:Long):Bitmap{val out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val c=Canvas(out);c.drawColor(Color.BLACK);drawFit(c,src,Rect(0,0,w,h));val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=22f;setShadowLayer(4f,0f,2f,Color.BLACK)};c.drawText(SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(Date(time)),16f,h-22f,p);return out}
    private fun drawFit(c:Canvas,b:Bitmap,dst:Rect){val scale=maxOf(dst.width().toFloat()/b.width,dst.height().toFloat()/b.height);val sw=(dst.width()/scale).toInt();val sh=(dst.height()/scale).toInt();val src=Rect((b.width-sw)/2,(b.height-sh)/2,(b.width+sw)/2,(b.height+sh)/2);c.drawBitmap(b,src,dst,Paint(Paint.ANTI_ALIAS_FLAG));b.recycle()}
    private fun toI420(b:Bitmap):ByteArray{val w=b.width;val h=b.height;val px=IntArray(w*h);b.getPixels(px,0,w,0,0,w,h);val out=ByteArray(w*h*3/2);var yi=0;var ui=w*h;var vi=ui+w*h/4;for(y in 0 until h){for(x in 0 until w){val c=px[y*w+x];val r=Color.red(c);val g=Color.green(c);val bl=Color.blue(c);out[yi++]=((66*r+129*g+25*bl+128 shr 8)+16).coerceIn(0,255).toByte();if(y%2==0&&x%2==0){out[ui++]=((-38*r-74*g+112*bl+128 shr 8)+128).coerceIn(0,255).toByte();out[vi++]=((112*r-94*g-18*bl+128 shr 8)+128).coerceIn(0,255).toByte()}}};return out}
    private fun createImage(name:String)=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");if(Build.VERSION.SDK_INT>=29){put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Selfie Memory");put(MediaStore.Images.Media.IS_PENDING,1)}})!!
    private fun finish(uri:Uri){if(Build.VERSION.SDK_INT>=29)context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)}
    private fun stamp()=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
}
