package com.mxmariner.tides.main.viewmodel

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.content.res.Resources
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import com.mxmariner.mxtide.api.StationType
import com.mxmariner.tides.R
import com.mxmariner.tides.main.adapter.TidesRecyclerAdapter
import com.mxmariner.tides.main.model.TidesViewState
import com.mxmariner.tides.main.model.TidesViewStateLoadingComplete
import com.mxmariner.tides.main.model.TidesViewStateLoadingStarted
import com.mxmariner.tides.repository.HarmonicsRepo
import com.mxmariner.tides.ui.SnackbarController
import com.mxmariner.tides.util.LocationPermissionResult
import com.mxmariner.tides.util.LocationResultPermission
import com.mxmariner.tides.util.RxLocation
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers


class TidesViewModelFactory(private val kodein: Kodein) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return kodein.instance<TidesViewModel>() as T
    }
}

class TidesViewModel(kodein: Kodein) : ViewModel() {

    private val harmonicsRepo: HarmonicsRepo = kodein.instance()
    private val rxLocation: RxLocation = kodein.instance()
    private val resources: Resources = kodein.instance()
    private val snackbarController: SnackbarController = kodein.instance()
    val recyclerAdapter: TidesRecyclerAdapter = kodein.instance()

    fun viewState(stationType: StationType): Observable<TidesViewState> {
        val loadingStarted = TidesViewStateLoadingStarted(resources.getString(R.string.finding_closest_tide_stations))
        val liststation = harmonicsRepo.tidesAndCurrents.stationNames

        for (s in liststation) {
            println("TEST = " + harmonicsRepo.tidesAndCurrents.stationCount)
            println(s)
        }

        println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^=============================================^^^^^^^^^^^^^^^^^^")
        println(harmonicsRepo.tidesAndCurrents.stationCount)

        return rxLocation.singleRecentLocationPermissionResult()
                .toObservable()
                .compose(snackbarController.retryWhenSnackbarUntilType<LocationPermissionResult, LocationResultPermission>())
                .observeOn(Schedulers.computation())
                .map {
                    harmonicsRepo.tidesAndCurrents.findNearestStations(it.location.latitude, it.location.longitude, stationType, 25)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .map<TidesViewState> {
                    recyclerAdapter.add(it)
                    TidesViewStateLoadingComplete()
                }
                .onErrorReturn { TidesViewStateLoadingComplete(resources.getString(R.string.could_not_deterine_location)) }
                .startWith(loadingStarted)
                .takeUntil {
                    when (it) {
                        is TidesViewStateLoadingComplete -> true
                        else -> false
                    }
                }
    }
}
