package com.mxmariner.tides.station

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import com.github.salomonbrys.kodein.instance
import com.jakewharton.rxbinding2.view.RxView
import com.mxmariner.mxtide.api.IStation
import com.mxmariner.mxtide.api.ITidesAndCurrents
import com.mxmariner.tides.factory.StationPresentationFactory
import com.mxmariner.tides.model.StationPresentation
import com.mxmariner.tides.station.di.StationModuleInjector
import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_station.*
import org.joda.time.DateTime

class StationActivity : AppCompatActivity() {

  private lateinit var tidesAndCurrents: ITidesAndCurrents
  private lateinit var stationPresentationFactory: StationPresentationFactory
  private val compositeDisposable = CompositeDisposable()
  private val stationDate = BehaviorSubject.create<DateTime>()

  override fun onCreate(savedInstanceState: Bundle?) {
    val injector = StationModuleInjector.activityScopeAssembly(this)
    super.onCreate(savedInstanceState)
    tidesAndCurrents = injector.instance()
    stationPresentationFactory = injector.instance()
    setContentView(R.layout.activity_station)

    //madrona://mxmariner.com/tides/station?stationName=NameUriEncoded
    val name = intent.data.getQueryParameter("stationName")
    getStationMessage(name)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy(
            onSuccess = ::bindUi,
            onComplete = ::bindUiError
        )

    compositeDisposable.addAll(

        RxView.clicks(editTime)
            .withLatestFrom(stationDate, BiFunction<Any, DateTime, DateTime> { _, date ->
              date
            }).flatMapMaybe {
              userTimePick(it)
            }.flatMapMaybe {
              getStationMessage(name, it)
            }.subscribeBy(
                onNext = this::bindUi
            ),

        RxView.clicks(editDate)
            .withLatestFrom(stationDate, BiFunction<Any, DateTime, DateTime> { _, date ->
              date
            })
            .flatMapMaybe {
              userDatePick(it)
            }.flatMapMaybe {
              getStationMessage(name, it)
            }.subscribeBy(
                onNext = this::bindUi
            )
    )
  }

  override fun onDestroy() {
    super.onDestroy()
    compositeDisposable.clear()
  }

  private fun getStationMessage(name: String?, dateTime: DateTime = DateTime.now()): Maybe<StationPresentation> {
    return Maybe.create<IStation> { emitter ->
      tidesAndCurrents.findStationByName(name)?.let {
        emitter.onSuccess(it)
      } ?: {
        emitter.onComplete()
      }()
    }.map {
      stationDate.onNext(dateTime)
      stationPresentationFactory.createPresentation(it, hrs = 12, dateTime = dateTime)
    }.subscribeOn(Schedulers.io())
  }

  @SuppressLint("SetTextI18n")
  private fun bindUi(presentation: StationPresentation) {
    icon.setImageResource(presentation.icon)

    nameAndTime.leftDesc = presentation.name
    nameAndTime.rightDesc = "${presentation.midDateTimeFormatted}\n" +
        "${getString(R.string.scale)} ${presentation.scaleHours}${getString(R.string.hrs)}"

    positionAndTimeZone.leftDesc = presentation.position
    positionAndTimeZone.rightDesc = presentation.timeZone.toTimeZone().displayName
    //editDate.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent), android.graphics.PorterDuff.Mode.SRC_IN)
    //editTime.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent), android.graphics.PorterDuff.Mode.SRC_IN)

    distanceAndLevel.leftDesc = presentation.distance
    distanceAndLevel.rightDesc = presentation.predictionNow

    lineChart.applyPresentation(presentation)
    nowLine.setBackgroundColor(presentation.color)
  }

  private fun userTimePick(startDate: DateTime): Maybe<DateTime> {
    return Maybe.create<DateTime> { emitter ->
      var selectedDate: Pair<Int, Int>? = null
      val listener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
        selectedDate = Pair(hourOfDay, minute)
      }
      TimePickerDialog(this, listener, startDate.hourOfDay, startDate.minuteOfHour, false).apply {
        setOnDismissListener {
          emitter.takeUnless { it.isDisposed }?.let {
            selectedDate?.let { (hour, minute) ->
              emitter.onSuccess(DateTime(startDate.year, startDate.monthOfYear, startDate.dayOfMonth, hour, minute, 0, startDate.zone))
            } ?: emitter.onComplete()
          }
        }
        show()
      }
    }.subscribeOn(AndroidSchedulers.mainThread())
  }

  private fun userDatePick(startDate: DateTime): Maybe<DateTime> {
    return Maybe.create<DateTime> { emitter ->
      var selectedDate: DateTime? = null
      val listener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
        selectedDate = DateTime(year, month + 1, dayOfMonth, startDate.hourOfDay, startDate.minuteOfHour, startDate.secondOfMinute, startDate.zone)
      }
      DatePickerDialog(this, listener, startDate.year, startDate.monthOfYear - 1, startDate.dayOfMonth).apply {
        setOnDismissListener {
          emitter.takeUnless { it.isDisposed }?.let {
            selectedDate?.let {
              emitter.onSuccess(it)
            } ?: emitter.onComplete()
          }
        }
        show()
      }
    }.subscribeOn(AndroidSchedulers.mainThread())
  }

  private fun bindUiError() {
    messageLabel.text = getString(com.mxmariner.tides.R.string.whoops)
  }
}
