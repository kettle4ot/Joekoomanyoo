package com.ssafy.heritage.view.heritage

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.activityViewModels
import androidx.transition.Fade
import androidx.transition.TransitionInflater
import com.ssafy.heritage.R
import com.ssafy.heritage.adpter.HeritageReviewAdapter
import com.ssafy.heritage.adpter.OnItemClickListener
import com.ssafy.heritage.base.BaseFragment
import com.ssafy.heritage.data.dto.Heritage
import com.ssafy.heritage.data.dto.HeritageScrap
import com.ssafy.heritage.data.remote.api.UserService
import com.ssafy.heritage.data.remote.request.HeritageReviewRequest
import com.ssafy.heritage.data.remote.response.HeritageReviewListResponse
import com.ssafy.heritage.databinding.FragmentHeritageDetail2Binding
import com.ssafy.heritage.util.FileUtil
import com.ssafy.heritage.util.FormDataUtil
import com.ssafy.heritage.view.HomeActivity
import com.ssafy.heritage.view.dialog.SharedMyGroupListDialog
import com.ssafy.heritage.view.login.LoginActivity
import com.ssafy.heritage.viewmodel.GroupViewModel
import com.ssafy.heritage.viewmodel.HeritageViewModel
import com.ssafy.heritage.viewmodel.UserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.daum.mf.map.api.MapPOIItem
import net.daum.mf.map.api.MapPoint
import net.daum.mf.map.api.MapView
import okhttp3.MultipartBody


private const val TAG = "HeritageDetailFragment___"
private const val ARG_HERITAGE = "heritage"
private val PERMISSIONS_REQUIRED = Manifest.permission.ACCESS_FINE_LOCATION

class HeritageDetailFragment :
    BaseFragment<FragmentHeritageDetail2Binding>(R.layout.fragment_heritage_detail2),
    MapView.POIItemEventListener, OnItemClickListener {
    private lateinit var mapView: MapView

    private var heritage: Heritage? = null

    private lateinit var userService: UserService
    private val userViewModel by activityViewModels<UserViewModel>()
    private val heritageViewModel by activityViewModels<HeritageViewModel>()
    private val groupViewModel by activityViewModels<GroupViewModel>()
    private lateinit var heritageScrap: HeritageScrap

    private lateinit var heritageReview: HeritageReviewRequest
    private lateinit var heritageReviewAdapter: HeritageReviewAdapter
    private lateinit var heritageReviewListResponse: HeritageReviewListResponse
    var img_multipart: MultipartBody.Part? = null

    private lateinit var btnPlayAudio: Button
    var mediaPlayer: MediaPlayer? = null
    var audioCheck = false
    var audioManager: AudioManager? = null
    var focusRequest: AudioFocusRequest? = null

    private lateinit var callback: OnBackPressedCallback

    private val locationManager by lazy {
        requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    private var lat = 0.0    // ??????
    private var lng = 0.0    // ??????

    override fun onAttach(context: Context) {
        super.onAttach(context)

        (requireActivity() as HomeActivity).backPressedListener.register()

        // fragment?????? back?????? ??????????????? ?????? ??????
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                parentFragmentManager.popBackStack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onDetach() {
        super.onDetach()

        (requireActivity() as HomeActivity).backPressedListener.unregister()

        // ???????????? ?????? ??????
        callback.remove()
    }


    @SuppressLint("LongLogTag")
    override fun init() {

        (requireActivity() as HomeActivity).setStatusbarColor("trans")

        // ?????? ?????? ???????????? ????????? ??????
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.Main) {
                arguments?.let {
                    heritage = it.getSerializable(ARG_HERITAGE) as Heritage
                    Log.d(TAG, "init onCreate: $heritage")
                }
            }
            binding.heritage = this@HeritageDetailFragment.heritage
            Log.d(TAG, "init init: ${this@HeritageDetailFragment.heritage}")
        }
//        if (userViewModel.user.value?.profileImgUrl != heritageReviewListResponse.profileImgUrl) {
//            heritageReviewListResponse.reviewImgUrl = userViewModel.user.value?.profileImgUrl.toString()
//        }

        initView()

        initMap()

        initAdapter()

        initObserver()

        initClickListener()

        setMotion()

        setFocusListener()
    }

    private fun setFocusListener() = with(binding) {
        etReviewContent.setOnFocusChangeListener { view, b ->
            when (b) {
                true -> (requireActivity() as HomeActivity).fab.hide()
                else -> (requireActivity() as HomeActivity).fab.show()
            }
        }
    }

    private fun initView() = with(binding) {
        val flag = userViewModel.scrapList.value?.any { it.heritageSeq == heritage?.heritageSeq }
        btnScrap.isSelected = flag == true
    }

    @SuppressLint("LongLogTag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedElementEnterTransition =
            TransitionInflater.from(requireContext()).inflateTransition(android.R.transition.move)
        sharedElementReturnTransition =
            TransitionInflater.from(requireContext()).inflateTransition(android.R.transition.move)
        enterTransition = Fade()
        exitTransition = Fade()
    }

    override fun onStart() {
        super.onStart()
        heritageViewModel.getHeritageReviewList()
    }

    // ?????? ??????????????? ????????? ??? ????????????
    override fun onPause() {
        super.onPause()
        try {
            mediaPlayer?.pause()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    // Destroys the MediaPlayer instance when the app is closed
    override fun onStop() {
        super.onStop()
        if (mediaPlayer != null) {
            mediaPlayer!!.release()
            mediaPlayer = null
        }
    }

    private fun initAdapter() = with(binding) {
        heritageReviewAdapter =
            HeritageReviewAdapter(this@HeritageDetailFragment, userViewModel.user.value?.userSeq!!)
        binding.recyclerviewReviewList.adapter = heritageReviewAdapter
    }

    private fun initObserver() {
        heritageViewModel.heritageReviewList.observe(viewLifecycleOwner) {
            heritageReviewAdapter.submitList(it)
        }

        groupViewModel.message.observe(viewLifecycleOwner) {
            // viewModel?????? Toast????????? Event ?????????
            it.getContentIfNotHandled()?.let {
                makeToast(it)
            }
        }
//        userViewModel.user.observe(viewLifecycleOwner) {
//            binding.user = it
//        }
    }

    private fun initMap() = with(binding) {
        mapView = MapView(requireContext())

        // ?????? ?????? ????????? ??????
        mapView.setPOIItemEventListener(
            this@HeritageDetailFragment
        )

        map.addView(mapView)
        requestLocationPermissionLancher.launch(PERMISSIONS_REQUIRED)
    }

    private val requestLocationPermissionLancher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // PERMISSION GRANTED

                // ???????????? ????????? ????????? ??????
                setLocation(
                    heritage?.heritageLat!!.toDouble(),
                    heritage?.heritageLng!!.toDouble(),
                    0
                )

                // ?????? ???????????? ?????? ??????
                makeMarker()

            } else {
                // PERMISSION NOT GRANTED
                makeToast("?????? ????????? ???????????????")
            }
        }

    private fun setLocation(lat: Double, lng: Double, zoomLevel: Int) {
        mapView.setMapCenterPointAndZoomLevel(
            MapPoint.mapPointWithGeoCoord(
                lat,
                lng
            ), zoomLevel, true
        )
    }

    private fun makeMarker() {

        heritageViewModel.heritageList.value?.forEachIndexed { index, heritage ->
            MapPOIItem().apply {
                itemName = heritageViewModel.heritageList.value?.get(index)?.heritageName
                mapPoint =
                    MapPoint.mapPointWithGeoCoord(
                        heritage.heritageLat.toDouble(),
                        heritage.heritageLng.toDouble()
                    )
                markerType = MapPOIItem.MarkerType.BluePin // ???????????? ???????????? BluePin ?????? ??????.
                selectedMarkerType =
                    MapPOIItem.MarkerType.RedPin // ????????? ???????????????, ???????????? ???????????? RedPin ?????? ??????.
                showAnimationType = MapPOIItem.ShowAnimationType.SpringFromGround
                tag = index

                mapView.addPOIItem(this)
            }
        }
    }

    override fun onItemClick(position: Int) {
        // ?????? ?????? ?????? ??????
        heritageViewModel.deleteHeritageReview(
            // adapter?????? position ?????? ???????????? ????????????
            heritageReviewAdapter.currentList.get(position).heritageReviewSeq,
            heritageReviewAdapter.currentList.get(position).heritageSeq
        )
    }

    @SuppressLint("LongLogTag")
    private fun initClickListener() = with(binding) {

        btnScrap.setOnClickListener {

            // ????????? ??????
            // ?????? ?????? ????????? ????????? (any!!)
            var scrapCheck = userViewModel.scrapList.value?.any {
                it.heritageSeq == heritage?.heritageSeq
            }

            heritageScrap = HeritageScrap(
                heritageSeq = heritage?.heritageSeq!!,
                heritageScrapSeq = 0,
                userSeq = userViewModel.user.value?.userSeq!!
            )

            // scrapList ?????? ?????? heritageSeq ?????? ?????? ??????
            Log.d(TAG, "initClickListener: ${scrapCheck}")
            if (scrapCheck == true) {
                // ???????????? ????????? ????????? ??? ??? => ????????? ????????? ??? ?????? ??????
                userViewModel.deleteHeritageScrap(heritage?.heritageSeq!!)
                makeToast("???????????? ?????????????????????")
                btnScrap.isSelected = false
                scrapCheck = false
                binding.btnScrap.isSelected = false
            } else {
                // ????????? ????????? ??? ??? ?????? ??????
                userViewModel.insertHeritageScrap(heritageScrap)
                makeToast("????????? ???????????????")
                btnScrap.isSelected = true
                scrapCheck = true
                binding.btnScrap.isSelected = true
            }
        }

//        // ???????????? ?????? (SNS ???????????? ?????? ?????????,,,)
//        btnLink.setOnClickListener {
//            try {
//                val sendText = "????????? ??? ??????"
//                var url = "heritage://heritage/detail"
//                val sendIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
//                sendIntent.action = Intent.ACTION_SEND
//                sendIntent.putExtra(Intent.EXTRA_TEXT, sendText)
//                sendIntent.type = "text/plain"
//                startActivity(Intent.createChooser(sendIntent, "Share"))
//            } catch (ignored: ActivityNotFoundException) {
//                Log.d("test", "ignored : $ignored")
//            }
//        }

        val audioURL = "http://116.67.83.213/media/voice/krVoice/11/kr-11-00030000-11.mp3"
        Log.d(TAG, audioURL)
        mediaPlayer = MediaPlayer()
        mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)

        var mediaPosition = mediaPlayer!!.getCurrentPosition()

        imagebtnHeritageDetailVoicePlay.setOnClickListener {
            if (mediaPlayer!!.isPlaying) {
                try {
                    // ????????? ????????????
                    mediaPlayer!!.pause()
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
            } else {
                try {
                    // ????????? MediaPlayer??? ?????? ??????????????? ??????. ?????? ??????????????? ??????,,,,?????? ?????? ??? ????????? ?????? ??????????????????
                    mediaPlayer = MediaPlayer()
                    mediaPlayer!!.setDataSource(audioURL)
                    mediaPlayer!!.prepare()
//                    mediaPlayer!!.start()
                    mediaPlayer!!.seekTo(mediaPosition)
                    mediaPlayer!!.setOnPreparedListener {
                        mediaPlayer!!.start()
                    }
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
            }
        }

//        imagebtnHeritageDetailVoicePause.setOnClickListener {
//            if (mediaPlayer!!.isPlaying) {
//                mediaPlayer!!.pause()
//                audioCheck = true
//                Log.d(TAG, "Audio pause")
//            }
//        }

        // ???????????? ????????????
        btnLink.setOnClickListener {
            // ??????????????? ?????????
            val dialog = SharedMyGroupListDialog(heritage!!.heritageSeq)
            dialog.show(childFragmentManager, "SharedMyGroupListDialog")
        }

        // ?????? ?????? ?????? ?????? ???
        btnImgAttach.setOnClickListener {
            requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // ?????? ????????? ?????? ?????? ???
        btnCreateReview.setOnClickListener {

            CoroutineScope(Dispatchers.Main).launch {
                if (!etReviewContent.text.isNullOrBlank() && img_multipart?.let {
                        heritageViewModel.sendImage(
                            it
                        )
                    } == true) {

                    heritageReview = HeritageReviewRequest(
                        userSeq = userViewModel.user.value?.userSeq!!,
                        heritageSeq = heritageViewModel.heritage.value?.heritageSeq!!,
                        heritageReviewText = etReviewContent.text.toString(),
                        reviewImgUrl = heritageViewModel.insertHeritageReview?.value!!,
                        userNickname = userViewModel.user.value?.userNickname!!,
                        profileImgUrl = userViewModel.user.value?.profileImgUrl!!
                    )
                    heritageViewModel.insertHeritageReview(heritageReview)
                    Log.d(TAG, "?????? ?????? ???: ${heritageReview}")
                    etReviewContent.setText("")

                } else if (!etReviewContent.text.isNullOrBlank() && img_multipart == null) {
                    heritageReview = HeritageReviewRequest(
                        userSeq = userViewModel.user.value?.userSeq!!,
                        heritageSeq = heritageViewModel.heritage.value?.heritageSeq!!,
                        heritageReviewText = etReviewContent.text.toString(),
                        reviewImgUrl = "",
                        userNickname = userViewModel.user.value?.userNickname!!,
                        profileImgUrl = userViewModel.user.value?.profileImgUrl!!
                    )
                    heritageViewModel.insertHeritageReview(heritageReview)
                    Log.d(TAG, "?????? ????????? ???: ${heritageReview}")
                    etReviewContent.setText("")
                } else {
                    makeToast("????????? ??????????????????")
                }
                img_multipart = null
            }
        }

//        // ?????? ??? ?????????
//        constraintContent1.setOnClickListener {
//            motionlayout1.transitionToEnd()
//        }
//        constraintContent2.setOnClickListener {
//            motionlayout1.transitionToStart()
//        }

        // ??? ?????? ?????????
        btnMyLocation.setOnClickListener {
            getLastLocation()
            mapView.setMapCenterPoint(
                MapPoint.mapPointWithGeoCoord(
                    lat, lng
                ), true
            )

            val item = mapView.poiItems.find { it.tag == -1 }
            item?.let { mapView.removePOIItem(item) }

            MapPOIItem().apply {
                itemName = "?????? ??????"
                mapPoint =
                    MapPoint.mapPointWithGeoCoord(
                        lat,
                        lng
                    )

                val bitmapdraw: BitmapDrawable = ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_current_location,
                    null
                ) as BitmapDrawable
                val b: Bitmap = bitmapdraw.bitmap
                val marker: Bitmap = Bitmap.createScaledBitmap(b, 100, 100, false)

                markerType = MapPOIItem.MarkerType.CustomImage          // ?????? ?????? (?????????)
                selectedMarkerType = MapPOIItem.MarkerType.CustomImage  // ?????? ??? ?????? ?????? (?????????)
                customImageBitmap = marker
                customSelectedImageBitmap = marker

                showAnimationType = MapPOIItem.ShowAnimationType.DropFromHeaven

                tag = -1

                mapView.addPOIItem(this)
            }
        }

        // ??? ??????
        btnUp.setOnClickListener {
            motionlayout1.transitionToEnd()
        }

        // ??? ??????
        btnDown.setOnClickListener {
            motionlayout1.transitionToStart()
        }

        // ?????? ?????? ??????
        btnReviewShow.setOnClickListener {
            it.isSelected = !it.isSelected
            if (it.isSelected) {
                recyclerviewReviewList.visibility = android.view.View.VISIBLE
            } else {
                recyclerviewReviewList.visibility = android.view.View.GONE
            }
        }
    }

    private fun setMotion() = with(binding) {
        // ?????????????????? ??????
        motionlayout1.addTransitionListener(object : MotionLayout.TransitionListener {
            override fun onTransitionStarted(p0: MotionLayout?, p1: Int, p2: Int) {}

            override fun onTransitionChange(p0: MotionLayout?, p1: Int, p2: Int, p3: Float) {}

            override fun onTransitionCompleted(p0: MotionLayout?, p1: Int) {
                if (p0?.progress == 1.0F) {
                    setLocation(
                        heritage?.heritageLat!!.toDouble(),
                        heritage?.heritageLng!!.toDouble(),
                        -1
                    )
                } else if (p0?.progress == 0.0F) {
                    setLocation(
                        heritage?.heritageLat!!.toDouble(),
                        heritage?.heritageLng!!.toDouble(),
                        1
                    )
                }
            }

            override fun onTransitionTrigger(p0: MotionLayout?, p1: Int, p2: Boolean, p3: Float) {}

        })
    }

    private fun getLastLocation() {

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissionLancher.launch(PERMISSIONS_REQUIRED)
            return
        }
        locationManager
            .getLastKnownLocation(LocationManager.GPS_PROVIDER)
            .apply {
                if (this != null) {
                    lat = latitude
                    lng = longitude
                }
            }

        locationManager
            .getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            .apply {
                if (this != null) {
                    lat = latitude
                    lng = longitude
                }
            }

        locationManager
            .getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            .apply {
                if (this != null) {
                    lat = latitude
                    lng = longitude
                }
            }
    }

    val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // PERMISSION GRANTED
            pick()
        } else {
            // PERMISSION NOT GRANTED
            makeToast("????????? ?????????")
        }
    }

    // ?????? ??????
    private fun pick() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        filterActivityLauncher.launch(intent)
    }

    // ?????? ????????? ????????? ??????
    private val filterActivityLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK && it.data != null) {

                val imagePath = it.data!!.data
                Log.d(TAG, "?????? ?????? ????????? imagePath: $imagePath")
                val file = FileUtil.toFile(requireContext(), imagePath!!)
                img_multipart = FormDataUtil.getImageBody("file", file)

            } else if (it.resultCode == Activity.RESULT_CANCELED) {
                makeToast("?????? ?????? ??????")
            } else {
                Log.d("ActivityResult", "something wrong")
            }
        }

    companion object {
        fun newInstance(param: Heritage) = HeritageDetailFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_HERITAGE, param)
            }
        }
    }

    override fun onPOIItemSelected(p0: MapView?, poiItem: MapPOIItem?) {

        if (poiItem?.tag ?: -1 >= 0) {
            val data = poiItem?.tag?.let { heritageViewModel.heritageList.value?.get(it) }

            mapView.setMapCenterPoint(
                MapPoint.mapPointWithGeoCoord(
                    data?.heritageLat!!.toDouble(), data?.heritageLng!!.toDouble()
                ), true
            )
            heritage = data
            binding.heritage = data
        }
    }

    override fun onCalloutBalloonOfPOIItemTouched(p0: MapView?, p1: MapPOIItem?) {
    }

    override fun onCalloutBalloonOfPOIItemTouched(
        p0: MapView?,
        p1: MapPOIItem?,
        p2: MapPOIItem.CalloutBalloonButtonType?
    ) {
    }

    override fun onDraggablePOIItemMoved(p0: MapView?, p1: MapPOIItem?, p2: MapPoint?) {
    }

    private fun makeToast(msg: String) {
        Toast.makeText(requireActivity(), msg, Toast.LENGTH_SHORT).show()
    }
}