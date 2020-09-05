package com.pengke.paper.scanner.crop

import android.Manifest
import android.R.attr
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.View
import android.widget.Toast
import com.pengke.paper.scanner.SourceManager
import com.pengke.paper.scanner.processor.Corners
import com.pengke.paper.scanner.processor.TAG
import com.pengke.paper.scanner.processor.cropPicture
import com.pengke.paper.scanner.processor.enhancePicture
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream


const val IMAGES_DIR = "smart_scanner"

class CropPresenter(val context: Context, private val iCropView: ICropView.Proxy) {
    private val picture: Mat? = SourceManager.pic

    private val corners: Corners? = SourceManager.corners
    private var croppedPicture: Mat? = null
    private var enhancedPicture: Bitmap? = null
    private var croppedBitmap: Bitmap? = null

    init {
        iCropView.getPaperRect().onCorners2Crop(corners, picture?.size())
        val bitmap = Bitmap.createBitmap(picture?.width() ?: 1080, picture?.height()
                ?: 1920, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(picture, bitmap, true)
        iCropView.getPaper()[0].setImageBitmap(bitmap)

        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val grayImage: Mat
        val cannedImage: Mat
        val kernel: Mat = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(10.0, 10.0))
        val dilate: Mat
        val size = Size(src.size().width, src.size().height)
        grayImage = Mat(size, CvType.CV_8UC4)
        cannedImage = Mat(size, CvType.CV_8UC1)
        dilate = Mat(size, CvType.CV_8UC1)

        Imgproc.cvtColor(src, grayImage, Imgproc.COLOR_BGR2GRAY)
        val greyBitmap=Bitmap.createBitmap(grayImage.cols(), grayImage.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(grayImage, greyBitmap)
        iCropView.getPaper()[1].setImageBitmap(greyBitmap)

        Imgproc.GaussianBlur(grayImage, grayImage, Size(5.0, 5.0), 0.0)
        val blurBitmap=Bitmap.createBitmap(grayImage.cols(), grayImage.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(grayImage, blurBitmap)
        iCropView.getPaper()[2].setImageBitmap(blurBitmap)

        Imgproc.threshold(grayImage, grayImage, 30.0, 255.0, Imgproc.THRESH_TRIANGLE)
        val thresBitmap=Bitmap.createBitmap(grayImage.cols(), grayImage.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(grayImage, thresBitmap)
        iCropView.getPaper()[3].setImageBitmap(thresBitmap)

        Imgproc.Canny(grayImage, cannedImage, 5.0, 10.0)
        val cannedBitmap=Bitmap.createBitmap(cannedImage.cols(), cannedImage.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(cannedImage, cannedBitmap)
        iCropView.getPaper()[4].setImageBitmap(cannedBitmap)

        Imgproc.dilate(cannedImage, dilate, kernel)

        // Don't do that at home or work it's for visualization purpose.

        // Don't do that at home or work it's for visualization purpose.
        val resultBitmap = Bitmap.createBitmap(dilate.cols(), dilate.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dilate, resultBitmap)
        iCropView.getPaper()[5].setImageBitmap(resultBitmap);
    }

    fun addImageToGallery(filePath: String, context: Context) {

        val values = ContentValues()

        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        values.put(MediaStore.MediaColumns.DATA, filePath)

        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    fun crop() {
        if (picture == null) {
            Log.i(TAG, "picture null?")
            return
        }

        if (croppedBitmap != null) {
            Log.i(TAG, "already cropped")
            return
        }

        Observable.create<Mat> {
            it.onNext(cropPicture(picture, iCropView.getPaperRect().getCorners2Crop()))
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { pc ->
                    Log.i(TAG, "cropped picture: " + pc.toString())
                    croppedPicture = pc
                    croppedBitmap = Bitmap.createBitmap(pc.width(), pc.height(), Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(pc, croppedBitmap)
                    iCropView.getCroppedPaper().setImageBitmap(croppedBitmap)
                    iCropView.getPaper().listIterator().forEach {
                     imageView -> imageView.visibility = View.GONE
                    }
                    iCropView.getPaperRect().visibility = View.GONE
                }
    }

    fun enhance() {
        if (croppedBitmap == null) {
            Log.i(TAG, "picture null?")
            return
        }

        Observable.create<Bitmap> {
            it.onNext(enhancePicture(croppedBitmap))
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { pc ->
                    enhancedPicture = pc
                    iCropView.getCroppedPaper().setImageBitmap(pc)
                }
    }

    fun save() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "please grant write file permission and trya gain", Toast.LENGTH_SHORT).show()
        } else {
            val dir = File(Environment.getExternalStorageDirectory(), IMAGES_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            //first save enhanced picture, if picture is not enhanced, save cropped picture, otherwise nothing to do
            val pic = enhancedPicture
            if (null != pic) {
                val file = File(dir, "enhance_${SystemClock.currentThreadTimeMillis()}.jpeg")
                val outStream = FileOutputStream(file)
                pic.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                outStream.flush()
                outStream.close()
                addImageToGallery(file.absolutePath, this.context)
                Toast.makeText(context, "picture saved, path: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            } else {
                val cropPic = croppedBitmap
                if (null != cropPic) {
                    val file = File(dir, "crop_${SystemClock.currentThreadTimeMillis()}.jpeg")
                    val outStream = FileOutputStream(file)
                    cropPic.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                    outStream.flush()
                    outStream.close()
                    addImageToGallery(file.absolutePath, this.context)
                    Toast.makeText(context, "picture saved, path: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}