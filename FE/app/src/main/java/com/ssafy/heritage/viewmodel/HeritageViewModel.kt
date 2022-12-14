package com.ssafy.heritage.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.heritage.data.dto.Heritage
import com.ssafy.heritage.data.dto.HeritageReview
import com.ssafy.heritage.data.dto.HeritageScrap
import com.ssafy.heritage.data.remote.request.HeritageReviewRequest
import com.ssafy.heritage.data.remote.response.HeritageReviewListResponse
import com.ssafy.heritage.data.repository.Repository
import com.ssafy.heritage.util.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import org.json.JSONObject
import retrofit2.Response

private const val TAG = "HeritageViewModel___"

class HeritageViewModel : ViewModel() {

    var job: Job? = null

    private val repository = Repository.get()

    private val _heritageList = MutableLiveData<List<Heritage>>()
    val heritageList: LiveData<List<Heritage>>
        get() = _heritageList

    private val _heritageCategory = MutableLiveData<Int>().apply { value = 0 }
    val heritageCategory: LiveData<Int>
        get() = _heritageCategory

    private val _heritageSort = MutableLiveData<Int>().apply { value = 0 }
    val heritageSort: LiveData<Int>
        get() = _heritageSort

    private val _lat = MutableLiveData<String>().apply { value = "0" }
    val lat: LiveData<String>
        get() = _lat

    private val _lng = MutableLiveData<String>().apply { value = "0" }
    val lng: LiveData<String>
        get() = _lng

    private val _heritage = MutableLiveData<Heritage>()
    val heritage: LiveData<Heritage>
        get() = _heritage

    private val _heritageReviewList = MutableLiveData<List<HeritageReviewListResponse>>()
    val heritageReviewList: MutableLiveData<List<HeritageReviewListResponse>>
        get() = _heritageReviewList

    private val _insertHeritageReview = SingleLiveEvent<String>()
    val insertHeritageReview: LiveData<String>
        get() = _insertHeritageReview

    private val _heritageReview = MutableLiveData<HeritageReview>()
    val heritageReview: LiveData<HeritageReview>
        get() = _heritageReview

    private val _heritageScrap = MutableLiveData<HeritageScrap>()
    val heritageScrap: MutableLiveData<HeritageScrap>
        get() = _heritageScrap

    private val _emptyLayoutVisible = MutableLiveData(false)
    val emptyLayoutVisible: LiveData<Boolean>
        get() = _emptyLayoutVisible

    // ?????? ???????????? ?????? ?????????
    fun getHeritageList() {
        viewModelScope.launch(Dispatchers.Main) {
            repository.selectAllHeritage().let { response ->
                if (response.isSuccessful) {
                    var list = (response.body() as MutableList<Heritage>)
//                    list.sortBy { it.heritageScrapCnt } // ???????????? ??????
                    _heritageList.value = list
                } else {
                    Log.d(TAG, "${response.code()}")
                }

            }
        }
    }

    // private ?????? ????????? ??? ?????? ??????
    fun setHeritage(heritage: Heritage) {
        _heritage.postValue(heritage)
    }

    // ?????? ????????????
    fun test(list: List<Heritage>) {
        _heritageList.value = list
    }

    // ???????????? ?????? ?????? ?????? ????????????
    fun getHeritageReviewList() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.selectAllHeritageReviews(heritage.value!!.heritageSeq!!).let { response ->
                if (response.isSuccessful) {
                    var list = response.body()!! as MutableList<HeritageReviewListResponse>
//                    list.sortBy { it.heritageReviewRegistedAt }  // ???????????????
                    _heritageReviewList.postValue(list)
                    Log.d(TAG, "getHeritageReviewList: ${list}")
                } else {
                    Log.d(TAG, "${response.code()}")
                }
            }
        }
    }

    // ???????????? ?????? ?????? ??????
    fun insertHeritageReview(heritageReviewInfo: HeritageReviewRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertHeritageReview(heritageReviewInfo).let { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "insertHeritageReview: ?????? ?????? ??????")
                    getHeritageReviewList()
//                    var info = response.body()!! as HeritageReviewListResponse
//                    _insertHeritageReview.postValue(info)
                } else {
                    Log.d(TAG, "${response.code()}")
                }
            }
        }
    }

    // ?????? ????????????
    suspend fun sendImage(img: MultipartBody.Part) = withContext(Dispatchers.Main) {

        var response: Response<String>? = null
        job = launch(Dispatchers.Main) {
            response = repository.sendImage(img)
        }
        job?.join()

        response?.let {
            Log.d(TAG, "sendImage response: $it")
            if (it.isSuccessful) {
                val body = JSONObject(it.body())
                val url = body.get("fileDownloadUri")
                Log.d(TAG, "sendImage body: ${url}")
                _insertHeritageReview.value = url as String
                Log.d(TAG, "sendImage: ${url as String}")
                Log.d(TAG, "sendImage: ${_insertHeritageReview.value}")
                true
            } else {
                false
                Log.d(TAG, "sendImage: ?????? ")
            }
        }
    }

    // ???????????? ?????? ?????? ??????
    fun deleteHeritageReview(heritageReviewSeq: Int, heritageSeq: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteHeritageReview(heritageReviewSeq, heritageSeq).let { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "deleteHeritageReview: ?????? ?????? ??????")
                    getHeritageReviewList()
                } else {
                    Log.d(TAG, "deleteHeritageReview: ${response.code()}")
                }
            }
        }
    }

    // ??????????????? ???????????? ?????? ????????? ??????
    suspend fun orderByLocation(map: HashMap<String, String>) = withContext(Dispatchers.Main) {
        var response: Response<List<Heritage>>? = null
        job = launch(Dispatchers.Main) {
            response = repository.orderByLocation(map)
        }
        job?.join()

        response?.let {
            Log.d(TAG, "orderByLocation response: $it")
            if (it.isSuccessful) {
                Log.d(TAG, "orderByLocation body: ${it.body()}")
                it.body()
            } else {
                Log.d(TAG, "orderByLocation: ${it.errorBody()}")
                null
            }
        }
    }

    // ????????????, ????????? ???????????? ????????? ??????
    suspend fun getOrderHeritage() = withContext(Dispatchers.Main) {

        val map = HashMap<String, Any>()
        map.put("categorySeq", _heritageCategory.value!!)
        map.put("sortSeq", _heritageSort.value!!)
        map.put("lat", _lat.value!!)
        map.put("lng", _lng.value!!)

        Log.d(TAG, "getOrderHeritage: ${map.keys}")
        Log.d(TAG, "getOrderHeritage: ${map.values}")

        var response: Response<List<Heritage>>? = null
        job = launch(Dispatchers.Main) {
            response = repository.getOrderHeritage(map)
        }
        job?.join()

        response?.let {
            Log.d(TAG, "getOrderHeritage response: $it")
            if (it.isSuccessful) {
                Log.d(TAG, "getOrderHeritage body: ${it.body()}")
                _heritageList.postValue(it.body())
            } else {
                Log.d(TAG, "getOrderHeritage error: ${it.errorBody()}")
                null
            }
        }
    }

    // ???????????? ??????
    fun setCategory(num: Int) = viewModelScope.launch {
        Log.d(TAG, "setCategory: $num")
        withContext(this.coroutineContext) { _heritageCategory.value = num }
        getOrderHeritage()
    }

    // ?????? ??????
    fun setSort(num: Int) = viewModelScope.launch {
        Log.d(TAG, "setSort: $num")
        withContext(this.coroutineContext) { _heritageSort.value = num }
        getOrderHeritage()
    }

    // ?????? ??????
    fun setLocation(lat: String, lng: String) = viewModelScope.launch {
        Log.d(TAG, "setLocation: $lat $lng")
        _lat.value = lat
        _lng.value = lng
        getOrderHeritage()
    }
}