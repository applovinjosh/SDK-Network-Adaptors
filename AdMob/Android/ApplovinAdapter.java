package com.applovin.mediation;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.applovin.adview.AppLovinIncentivizedInterstitial;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdClickListener;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdRewardListener;
import com.applovin.sdk.AppLovinAdVideoPlaybackListener;
import com.applovin.sdk.AppLovinErrorCodes;
import com.applovin.sdk.AppLovinSdk;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.OnContextChangedListener;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.mediation.MediationRewardedVideoAdAdapter;
import com.google.android.gms.ads.reward.mediation.MediationRewardedVideoAdListener;

import java.lang.reflect.Method;
import java.util.Map;

import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;

/**
 * AppLovin SDK rewarded video adapter for AdMob.
 * <p>
 * Created by Thomas So on 5/29/17.
 *
 * @version 2.0
 */

public class ApplovinAdapter
        implements MediationRewardedVideoAdAdapter, OnContextChangedListener,
        AppLovinAdLoadListener, AppLovinAdDisplayListener, AppLovinAdClickListener, AppLovinAdVideoPlaybackListener, AppLovinAdRewardListener
{
    private static final boolean LOGGING_ENABLED = true;
    private static final Handler uiHandler       = new Handler( Looper.getMainLooper() );

    private boolean initialized;

    private AppLovinIncentivizedInterstitial incentivizedInterstitial;
    private Context                          context;
    private MediationRewardedVideoAdListener listener;

    private boolean    fullyWatched;
    private RewardItem reward;

    //
    // AdMob Custom Event Methods
    //

    @Override
    public void initialize(final Context context, final MediationAdRequest adRequest, final String userId, final MediationRewardedVideoAdListener listener, final Bundle serverParameters, final Bundle networkExtras)
    {
        // SDK versions BELOW 7.2.0 require a instance of an Activity to be passed in as the context
        if ( AppLovinSdk.VERSION_CODE < 720 && !( context instanceof Activity ) )
        {
            log( ERROR, "Unable to request AppLovin rewarded video. Invalid context provided." );
            listener.onInitializationFailed( this, AdRequest.ERROR_CODE_INVALID_REQUEST );

            return;
        }

        log( DEBUG, "Initializing AppLovin rewarded video..." );

        this.context = context;
        this.listener = listener;

        if ( !initialized )
        {
            AppLovinSdk.initializeSdk( context );
            AppLovinSdk.getInstance( context ).setPluginVersion( "AdMob-2.0" );

            initialized = true;

            incentivizedInterstitial = AppLovinIncentivizedInterstitial.create( context );
        }

        listener.onInitializationSucceeded( this );
    }

    @Override
    public boolean isInitialized()
    {
        return initialized;
    }

    @Override
    public void loadAd(final MediationAdRequest adRequest, final Bundle serverParameters, final Bundle networkExtras)
    {
        log( DEBUG, "Requesting AppLovin rewarded video with networkExtras: " + networkExtras );

        if ( incentivizedInterstitial.isAdReadyToDisplay() )
        {
            listener.onAdLoaded( this );
        }
        else
        {
            incentivizedInterstitial.preload( this );
        }
    }

    @Override
    public void showVideo()
    {
        if ( incentivizedInterstitial.isAdReadyToDisplay() )
        {
            fullyWatched = false;
            reward = null;

            try
            {
                // AppLovin SDK < 7.2.0 uses an Activity, as opposed to Context in >= 7.2.0
                final Class<?> contextClass = ( AppLovinSdk.VERSION_CODE < 720 ) ? Activity.class : Context.class;
                final Method showMethod = AppLovinIncentivizedInterstitial.class.getMethod( "show",
                                                                                            contextClass,
                                                                                            String.class,
                                                                                            AppLovinAdRewardListener.class,
                                                                                            AppLovinAdVideoPlaybackListener.class,
                                                                                            AppLovinAdDisplayListener.class,
                                                                                            AppLovinAdClickListener.class );

                try
                {
                    showMethod.invoke( incentivizedInterstitial, context, null, this, this, this, this );
                }
                catch ( Throwable th )
                {
                    log( ERROR, "Unable to invoke show() method from AppLovinIncentivizedInterstitial." );
                    listener.onAdFailedToLoad( this, AdRequest.ERROR_CODE_INTERNAL_ERROR );
                }
            }
            catch ( Throwable th )
            {
                log( ERROR, "Unable to get show() method from AppLovinIncentivizedInterstitial." );
                listener.onAdFailedToLoad( this, AdRequest.ERROR_CODE_INTERNAL_ERROR );
            }
        }
        else
        {
            log( ERROR, "Failed to show an AppLovin rewarded video before one was loaded" );
            listener.onAdFailedToLoad( this, AdRequest.ERROR_CODE_INTERNAL_ERROR );
        }
    }

    @Override
    public void onPause() {}

    @Override
    public void onResume() {}

    @Override
    public void onDestroy() {}

    @Override
    public void onContextChanged(final Context context)
    {
        if ( context != null )
        {
            log( DEBUG, "Context changed: " + context );
            this.context = context;
        }
    }

    //
    // Ad Load Listener
    //

    @Override
    public void adReceived(final AppLovinAd ad)
    {
        log( DEBUG, "Rewarded video did load ad: " + ad.getAdIdNumber() );

        runOnUiThread( new Runnable()
        {
            @Override
            public void run()
            {
                listener.onAdLoaded( ApplovinAdapter.this );
            }
        } );
    }

    @Override
    public void failedToReceiveAd(final int errorCode)
    {
        log( DEBUG, "Rewarded video failed to load with error: " + errorCode );

        runOnUiThread( new Runnable()
        {
            @Override
            public void run()
            {
                listener.onAdFailedToLoad( ApplovinAdapter.this, toAdMobErrorCode( errorCode ) );
            }
        } );
    }

    //
    // Ad Display Listener
    //

    @Override
    public void adDisplayed(final AppLovinAd ad)
    {
        log( DEBUG, "Rewarded video displayed" );
        listener.onAdOpened( this );
    }

    @Override
    public void adHidden(final AppLovinAd ad)
    {
        log( DEBUG, "Rewarded video dismissed" );

        if ( fullyWatched && reward != null )
        {
            log( DEBUG, "Rewarded " + reward.getAmount() + " " + reward.getType() );
            listener.onRewarded( this, reward );
        }

        listener.onAdClosed( this );
    }

    //
    // Ad Click Listener
    //

    @Override
    public void adClicked(final AppLovinAd ad)
    {
        log( DEBUG, "Rewarded video clicked" );

        listener.onAdClicked( this );
        listener.onAdLeftApplication( this );
    }

    //
    // Video Playback Listener
    //

    @Override
    public void videoPlaybackBegan(AppLovinAd ad)
    {
        log( DEBUG, "Rewarded video playback began" );
        listener.onVideoStarted( this );
    }

    @Override
    public void videoPlaybackEnded(AppLovinAd ad, double percentViewed, boolean fullyWatched)
    {
        log( DEBUG, "Rewarded video playback ended at playback percent: " + percentViewed );
        this.fullyWatched = fullyWatched;
    }

    //
    // Reward Listener
    //

    @Override
    public void userOverQuota(final AppLovinAd appLovinAd, final Map map)
    {
        log( ERROR, "Rewarded video validation request for ad did exceed quota with response: " + map );
    }

    @Override
    public void validationRequestFailed(final AppLovinAd appLovinAd, final int errorCode)
    {
        log( ERROR, "Rewarded video validation request for ad failed with error code: " + errorCode );
    }

    @Override
    public void userRewardRejected(final AppLovinAd appLovinAd, final Map map)
    {
        log( ERROR, "Rewarded video validation request was rejected with response: " + map );
    }

    @Override
    public void userDeclinedToViewAd(final AppLovinAd appLovinAd)
    {
        log( DEBUG, "User declined to view rewarded video" );
    }

    @Override
    public void userRewardVerified(final AppLovinAd ad, final Map map)
    {
        final String currency = (String) map.get( "currency" );
        final String amountStr = (String) map.get( "amount" );
        final int amount = (int) Double.parseDouble( amountStr ); // AppLovin returns amount as double

        log( DEBUG, "Verified " + amount + " " + currency );

        reward = new AppLovinRewardItem( amount, currency );
    }

    //
    // Utility Methods
    //

    private static void log(final int priority, final String message)
    {
        if ( LOGGING_ENABLED )
        {
            Log.println( priority, "AppLovinRewardedVideo", message );
        }
    }

    private static int toAdMobErrorCode(final int applovinErrorCode)
    {
        if ( applovinErrorCode == AppLovinErrorCodes.NO_FILL )
        {
            return AdRequest.ERROR_CODE_NO_FILL;
        }
        else if ( applovinErrorCode == AppLovinErrorCodes.NO_NETWORK || applovinErrorCode == AppLovinErrorCodes.FETCH_AD_TIMEOUT )
        {
            return AdRequest.ERROR_CODE_NETWORK_ERROR;
        }
        else
        {
            return AdRequest.ERROR_CODE_INTERNAL_ERROR;
        }
    }

    /**
     * Reward item wrapper class.
     */
    private static final class AppLovinRewardItem
            implements RewardItem
    {
        private final int    amount;
        private final String type;

        private AppLovinRewardItem(final int amount, final String type)
        {
            this.amount = amount;
            this.type = type;
        }

        @Override
        public String getType()
        {
            return type;
        }

        @Override
        public int getAmount()
        {
            return amount;
        }
    }

    /**
     * Performs the given runnable on the main thread.
     */
    public static void runOnUiThread(final Runnable runnable)
    {
        if ( Looper.myLooper() == Looper.getMainLooper() )
        {
            runnable.run();
        }
        else
        {
            uiHandler.post( runnable );
        }
    }
}
