package com.example.minorproject

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ComponentActivity
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.Vibrator
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.firebase.FirebaseError
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_login.*
import kotlinx.android.synthetic.main.activity_my_trackers.*
import kotlinx.android.synthetic.main.contact_ticket.view.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.jar.Manifest
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity(), SensorEventListener {

    var sensor:Sensor?=null
    var sensorManager: SensorManager?=null
    var adapter: ContactAdapter?=null
    var listOfContact=ArrayList<UserContact>()
    var databaseRef:DatabaseReference?=null
    var locationManager : LocationManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //var myLocation = MyService.MyLocationListener()
        //val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3, 3f, myLocation)
        sensorManager=getSystemService(Context.SENSOR_SERVICE)as SensorManager
        sensor=sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val userData= UserData(this)

        if (MyService.isServiceRunning) return // Donot run again
        checkContactPermission()
        checkLocationPermission()
        checkSmsPermission()

        //val userData= UserData(this)
        userData.isFirstTimeLoad()

    }

    override fun onResume() {
        super.onResume()
        val userData= UserData(this)
        //userData.isFirstTimeLoad()
        databaseRef= FirebaseDatabase.getInstance().reference

        // For Deby=ug only
        //dummpyData()

        adapter = ContactAdapter(this, listOfContact)
        lvContactList.adapter= adapter
        lvContactList.onItemClickListener= AdapterView.OnItemClickListener{
                parent,view,postion,id ->
            val userInfo =listOfContact[postion]
            // get datatime
            val df =SimpleDateFormat("yyyy/MMM/dd HH:MM:ss")
            val date =Date()
            // save to database
            databaseRef!!.child("Users").child(userInfo.phoneNumber!!).child("request").setValue(df.format(date).toString())

            val intent =Intent(applicationContext,MapsActivity::class.java)
            intent.putExtra("phoneNumber",userInfo.phoneNumber)
            startActivity(intent)
        }
        refreshUsers()
        sensorManager!!.registerListener(this,sensor,SensorManager.SENSOR_DELAY_NORMAL)
        //val userData= UserData(this)
        if (userData.loadPhoneNumber()=="empty"){
            return
        }
        refreshUsers()


    }

    override fun onPause() {
        super.onPause()
        sensorManager!!.unregisterListener(this)
    }


    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }
    var isCancelled=false
    var isNotSent=false
    var notify=true
    var xold=0.0
    var yold=0.0
    var zold=0.0
    //Rwquired threshold 4000. For ease take 100
    var threshold=41.28
    var oldtime:Long=0
    override fun onSensorChanged(event: SensorEvent?) {
        var x = event!!.values[0]
        var y = event!!.values[1]
        var z = event!!.values[2]
        var currentTime = System.currentTimeMillis()
        if ((currentTime - oldtime) > 100) {
            var timeDiff = currentTime - oldtime
            oldtime = currentTime
            var speed = Math.abs(x + y + z - xold - yold - zold)
            //var speed=Math.abs(x+y+z)
           // Toast.makeText(applicationContext, speed.toString(), Toast.LENGTH_LONG).show()
            if (speed > threshold) {
                val mAlertDialog = AlertDialog.Builder(this)
                var v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

                val timer = object : CountDownTimer(20000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        if (!isCancelled) {
                            v.vibrate(500)
                            var message =
                                    "seconds remaining: " + (millisUntilFinished / 1000).toString()
                            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onFinish() {
                        if (!isNotSent) {
                            sendsms()
                //            isNotSent=true
                        }
                        notify=true
                    }
                }
                    timer.start()
                    mAlertDialog.setIcon(R.mipmap.ic_launcher_round) //set alertdialog icon
                    mAlertDialog.setTitle("ALERT!") //set alertdialog title
                    mAlertDialog.setMessage("Accident detected. Send message?") //set alertdialog message
                    mAlertDialog.setPositiveButton("Yes") { dialog, id ->
                        isCancelled = true
                        isNotSent = false
                        Toast.makeText(applicationContext, "Message will be sent", Toast.LENGTH_LONG).show()
                        notify=false
                    }
                    mAlertDialog.setNegativeButton("No") { dialog, id ->
                        Toast.makeText(applicationContext, "Message will not be sent", Toast.LENGTH_LONG).show()
                        isCancelled = true
                        isNotSent = true
                        notify=false
                    }
                    mAlertDialog.show()

            }
        }
    }

    var list=ArrayList<String>()
    var map1=HashMap<String,Any>()

    val SMS_CODE = 1;

    fun sendsms(){
        //var smsIntent = Intent(Intent.ACTION_SENDTO)
        //val smsManager = SmsManager.getDefault() as SmsManager

       // Toast.makeText(applicationContext, MyService.myLocation!!.latitude.toString(), Toast.LENGTH_LONG).show()

        val userData= UserData(this)
        databaseRef!!.child("Users").
            child(userData.loadPhoneNumber()).
            child("Finders").addValueEventListener(object :
            ValueEventListener {

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val td = dataSnapshot!!.value as HashMap<String, Any>
                //Toast.makeText(applicationContext, td.keys.toString() ,Toast.LENGTH_LONG).show()
                if(!isNotSent) {
                    for (key in td.keys) {
                        var message="https://www.latlong.net/c/?lat="+MyService.myLocation!!.latitude.toString()+"&long="+MyService.myLocation!!.longitude.toString()
                      //  Toast.makeText(applicationContext, key.toString(), Toast.LENGTH_LONG).show()
                      SmsManager.getDefault().sendTextMessage(key.toString(), null, "ALERT! It appears the person has met with an accident. Location. " + message, null, null)
                        //smsManager.sendTextMessage(key.toString(), null, "sms message", null, null)
                        //smsIntent.setData(Uri.parse(key))
                        //smsIntent.putExtra("Sms", "Body")
                        //if (smsIntent.resolveActivity(getPackageManager()) != null) {
                          //  startActivity(smsIntent)
                        }
                    }
                }


            override fun onCancelled(p0: DatabaseError) {

            }
        }
        )

    }


    fun refreshUsers(){
        val userData= UserData(this)
        databaseRef!!.child("Users").
            child(userData.loadPhoneNumber()).
            child("Finders").addValueEventListener(object :
            ValueEventListener{

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                try {
                    val td = dataSnapshot!!.value as HashMap<String,Any>

                    listOfContact.clear()

                    if (td==null){
                        listOfContact.add(UserContact("NO_USERS","nothing"))
                        adapter!!.notifyDataSetChanged()
                        return
                    }

                    for (key in td.keys){
                        val name = listOfContacts[key]
                        listOfContact.add(UserContact(name.toString() ,key))

                    }

                    adapter!!.notifyDataSetChanged()
                }catch (ex:Exception){
                    listOfContact.clear()
                    listOfContact.add(UserContact("NO_USERS","nothing"))
                    adapter!!.notifyDataSetChanged()
                    return
                }
            }

            override fun onCancelled(p0: DatabaseError) {

            }
        })
    }
    //for debug first time
    //fun dummpyData(){
      //  listOfContact.add(UserContact("hussein","3434"))
       // listOfContact.add(UserContact("jena","344343"))
        //listOfContact.add(UserContact("laya","434543"))
    //}

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater=menuInflater
        inflater.inflate(R.menu.main_menu,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item!!.itemId){
            R.id.addTracker ->{
                val intent= Intent(this,MyTrackers::class.java)
                startActivity(intent)
            }
            R.id.help ->{
                //TODO:: as k for help from friend
            }
            else ->{
                return super.onOptionsItemSelected(item)
            }
        }

        return true
    }

    class ContactAdapter: BaseAdapter {
        var listOfContact=ArrayList<UserContact>()
        var context: Context?=null
        constructor(context: Context, listOfContact:ArrayList<UserContact>){
            this.context=context
            this.listOfContact=listOfContact
        }
        override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
            val userContact = listOfContact[p0]

            if (userContact.name.equals("NO_USERS")){
                val inflator = context!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val contactTicketView = inflator.inflate(R.layout.no_user, null)
                return contactTicketView
            }else {
                val inflator = context!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val contactTicketView = inflator.inflate(R.layout.contact_ticket, null)
                contactTicketView.tvName.text = userContact.name
                contactTicketView.tvPhoneNumber.text = userContact.phoneNumber

                return contactTicketView
            }
        }

        override fun getItem(p0: Int): Any {

            return listOfContact[p0]
        }

        override fun getItemId(p0: Int): Long {
            return p0.toLong()
        }

        override fun getCount(): Int {

            return listOfContact.size
        }

    }

    val CONTACT_CODE =123
    fun checkContactPermission(){

        if(Build.VERSION.SDK_INT>=23){

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED ){

                requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), CONTACT_CODE)
                return
            }
        }
        loadContact()
    }

    fun checkSmsPermission(){

        if(Build.VERSION.SDK_INT>=23){

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) !=
                PackageManager.PERMISSION_GRANTED ){

                requestPermissions(arrayOf(android.Manifest.permission.SEND_SMS), SMS_CODE)
                return
            }
        }
        loadContact()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        when (requestCode) {
            CONTACT_CODE-> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadContact()
                } else {
                    Toast.makeText(this, "Cannot access to contact ", Toast.LENGTH_LONG).show()
                }
            }
            LOCATION_CODE->{
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getUserLocation()
                } else {
                    Toast.makeText(this, "Cannot access to location ", Toast.LENGTH_LONG).show()
                }
            }

            SMS_CODE->{
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sendsms()
                } else {
                    Toast.makeText(this, "Cannot send message ", Toast.LENGTH_LONG).show()
                }
            }
            else ->{
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }

        }


    }

    var listOfContacts=HashMap<String,String>()
    fun loadContact(){
        try{
            listOfContacts.clear()

            val cursor=contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,null,null,null,null)
            cursor!!.moveToFirst()
            do {
                val name=cursor!!.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val phoneNumber=cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))

                listOfContacts.put(UserData.formatPhoneNumber(phoneNumber),name)
            }while (cursor!!.moveToNext())
        }catch (ex:Exception){}
    }



    val LOCATION_CODE =124
    fun checkLocationPermission(){

        if(Build.VERSION.SDK_INT>=23){

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED ){

                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_CODE)
                return
            }
        }
        getUserLocation()
    }


    fun getUserLocation(){

        // Start service
        if(!MyService.isServiceRunning){
            val intent= Intent(baseContext,MyService::class.java)
            startService(intent)
        }
    }

}
