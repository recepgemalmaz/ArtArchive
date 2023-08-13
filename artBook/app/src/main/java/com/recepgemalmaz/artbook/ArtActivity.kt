package com.recepgemalmaz.artbook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.recepgemalmaz.artbook.databinding.ActivityArtBinding
import com.recepgemalmaz.artbook.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream

class ArtActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArtBinding

    private lateinit var activityResultLauncher : ActivityResultLauncher<Intent>    //galeriye gitmek icin
    private lateinit var permissionLauncher : ActivityResultLauncher<String>
    var selectedBitmap : Bitmap? = null
    private lateinit var database : SQLiteDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArtBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        database = this.openOrCreateDatabase("Arts", Context.MODE_PRIVATE,null)

        registerLauncher()

        val intent = intent

        val info = intent.getStringExtra("info")

        if (info.equals("new")) {
            binding.artName.setText("")
            binding.artistName.setText("")
            binding.year.setText("")
            binding.save.visibility = View.VISIBLE

            val selectedImageBackground = BitmapFactory.decodeResource(applicationContext.resources,R.drawable.yukle)
            binding.imageView.setImageBitmap(selectedImageBackground)

        } else {
            binding.save.visibility = View.INVISIBLE
            val selectedId = intent.getIntExtra("id",1)

            val cursor = database.rawQuery("SELECT * FROM arts WHERE id = ?", arrayOf(selectedId.toString()))

            val artNameIx = cursor.getColumnIndex("artname")
            val artistNameIx = cursor.getColumnIndex("artistname")
            val yearIx = cursor.getColumnIndex("year")
            val imageIx = cursor.getColumnIndex("image")

            while (cursor.moveToNext()) {
                binding.artName.setText(cursor.getString(artNameIx))
                binding.artistName.setText(cursor.getString(artistNameIx))
                binding.year.setText(cursor.getString(yearIx))

                val byteArray = cursor.getBlob(imageIx)
                val bitmap = BitmapFactory.decodeByteArray(byteArray,0,byteArray.size)
                binding.imageView.setImageBitmap(bitmap)

            }

            cursor.close()

        }

    }



    fun saveClick(view : View) {

        val artName = binding.artName.text.toString()
        val artistName = binding.artistName.text.toString()
        val year = binding.year.text.toString()

        if (selectedBitmap != null) {
            val smallBitmap = makeSmallerBitmap(selectedBitmap!!,300)

            //gorseli veriye cevirme islemi
            val outputStream = ByteArrayOutputStream()
            smallBitmap.compress(Bitmap.CompressFormat.PNG,50,outputStream)
            val byteArray = outputStream.toByteArray()

            try {

                database.execSQL("CREATE TABLE IF NOT EXISTS arts (id INTEGER PRIMARY KEY, artname VARCHAR, artistname VARCHAR, year VARCHAR, image BLOB)")

                val sqlString = "INSERT INTO arts (artname, artistname, year, image) VALUES (?, ?, ?, ?)"
                val statement = database.compileStatement(sqlString)
                statement.bindString(1, artName)
                statement.bindString(2, artistName)
                statement.bindString(3, year)
                statement.bindBlob(4, byteArray)

                statement.execute()

            } catch (e: Exception) {
                e.printStackTrace()
            }


            val intent = Intent(this@ArtActivity,MainActivity::class.java)

            //butun activityleri kapat ve main activitye git
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)

            //finish()
        }

    }
    fun selectImage(view: View) {
        //SDK 33 ve uzeri icin READ_MEDIA_IMAGES iznini kullan
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            //Kullanicidan izin alabilmiyiz alamadiysak izin isteme islemleri yapiyoruz burada
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                //kullanici izin ekrani cikti ancak daha once reddetmis
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.READ_MEDIA_IMAGES)) {
                    Snackbar.make(view, "Permission needed for gallery", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Give Permission",
                            View.OnClickListener {
                                permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                            }).show()

                    //ilk defa izin isteniyor
                } else {
                    permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                }
                //Kullanicidan izin alabilmissek bu else blogu calisacak
                //bu noktada activityResultLauncher ile galeriye gider
            } else {
                val intentToGallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGallery)
            }

        //SDK 33 alti icin READ_EXTERNAL_STORAGE iznini kullan
        } else {
            //Kullanicidan izin alabilmiyiz alamadiysak izin isteme islemleri yapiyoruz burada
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                //kullanici izin ekrani cikti ancak daha once reddetmis
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    Snackbar.make(view, "Permission needed for gallery", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Give Permission",
                            View.OnClickListener {
                                permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }).show()
                } else {
                    //ilk defa izin isteniyor
                    permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                //Kullanicidan izin alabilmissek bu else blogu calisacak
                //bu noktada activityResultLauncher ile galeriye gider
            } else {
                val intentToGallery =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGallery)
            }
        }
    }


    //izin isteme ve galeriye gitme islemleri icin
    private fun registerLauncher() {
        //izin alindiktan sonra galeriye gitmek icin
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intentFromResult = result.data
                if (intentFromResult != null) {
                    val imageData = intentFromResult.data
                    try {
                        //SDK 28 ve uzeri icin
                        if (Build.VERSION.SDK_INT >= 28) {
                            val source = ImageDecoder.createSource(this@ArtActivity.contentResolver, imageData!!)
                            selectedBitmap = ImageDecoder.decodeBitmap(source)
                            binding.imageView.setImageBitmap(selectedBitmap)
                        } else {
                            //SDK 28 alti icin
                            selectedBitmap = MediaStore.Images.Media.getBitmap(this@ArtActivity.contentResolver, imageData)
                            binding.imageView.setImageBitmap(selectedBitmap)
                        }
                    } catch (e : Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        //izin isteme blogu icin
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            //kullanicidan izin istedikten sonra burasi calisacak
            //kullanici izin verdiyse
            if (result) {
                //permission granted yani izin verildiyse
                val intentToGallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGallery)
            //kullanici izin vermediyse
            } else {
                //permission denied yani izin verilmediyse
                Toast.makeText(this@ArtActivity, "Permisson needed!", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun makeSmallerBitmap(image: Bitmap, maximumSize : Int) : Bitmap {
        var width = image.width
        var height = image.height

        val bitmapRatio : Double = width.toDouble() / height.toDouble()
        if (bitmapRatio > 1) {
            width = maximumSize
            val scaledHeight = width / bitmapRatio
            height = scaledHeight.toInt()
        } else {
            height = maximumSize
            val scaledWidth = height * bitmapRatio
            width = scaledWidth.toInt()
        }
        return Bitmap.createScaledBitmap(image,width,height,true)
    }

}














/*fun selectImage(view : View) {

    //kullanici galerisinden resim secmek istediginde
    if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

        //izin verilmemisse
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)) {


            Snackbar.make(view, "Permission needed for gallery", Snackbar.LENGTH_INDEFINITE).setAction("Give Permission", View.OnClickListener {

                //izin verilmemisse ve kullanici daha once izin vermemisse
                //izin iste
                permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)

            }).show()

        } else {

            //izin verilmemisse ve kullanici daha once izin vermemisse
            //izin iste
            permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }


    } else {

        //izin verilmisse galeriye git
        val intentToGallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        activityResultLauncher.launch(intentToGallery)
    }

}*/


//kullanici izin verdiyse
/*private fun registerLauncher(){

        //galeriye gitmek icin
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if(result.resultCode == RESULT_OK) {

                val intentFromResult = result.data
                if(intentFromResult != null) {

                    val imageData = intentFromResult.data
                    binding.imageView.setImageURI(imageData)
                    if(imageData != null) {
                        //binding.imageView.setImageURI(imageData)
                        try {
                            if (Build.VERSION.SDK_INT >= 28) {
                                val soruce = ImageDecoder.createSource(this@ArtActivity.contentResolver, imageData)
                                selectedBitmap = ImageDecoder.decodeBitmap(soruce)
                                binding.imageView.setImageBitmap(selectedBitmap)
                            } else {
                                selectedBitmap = MediaStore.Images.Media.getBitmap(contentResolver,imageData)
                                binding.imageView.setImageBitmap(selectedBitmap)

                            }

                        }catch (e : Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        //izin vermek icin yani izin istemek icin
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->

            if(result) {

                //izin verildiyse galeriye git
                val intentToGallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGallery)

            } else {

                //izin verilmediyse toast goster
                Toast.makeText(this@ArtActivity, "Permission needed!", Toast.LENGTH_LONG).show()

            }
        }*/