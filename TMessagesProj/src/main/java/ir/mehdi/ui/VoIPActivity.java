/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2016.
 */

package ir.mehdi.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.graphics.Palette;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import ir.mehdi.messenger.AndroidUtilities;
import ir.mehdi.messenger.ApplicationLoader;
import ir.mehdi.messenger.BuildVars;
import ir.mehdi.messenger.ContactsController;
import ir.mehdi.messenger.Emoji;
import ir.mehdi.messenger.ImageReceiver;
import ir.mehdi.messenger.LocaleController;
import ir.mehdi.messenger.NotificationCenter;
import ir.mehdi.messenger.R;

import ir.mehdi.messenger.Utilities;
import ir.mehdi.messenger.voip.EncryptionKeyEmojifier;
import ir.mehdi.messenger.voip.VoIPController;
import ir.mehdi.messenger.voip.VoIPService;
import ir.mehdi.tgnet.TLRPC;
import ir.mehdi.ui.ActionBar.AlertDialog;
import ir.mehdi.ui.ActionBar.BottomSheet;
import ir.mehdi.ui.ActionBar.Theme;
import ir.mehdi.ui.Components.BackupImageView;
import ir.mehdi.ui.Components.CorrectlyMeasuringTextView;
import ir.mehdi.ui.Components.CubicBezierInterpolator;
import ir.mehdi.ui.Components.IdenticonDrawable;
import ir.mehdi.ui.Components.LayoutHelper;
import ir.mehdi.ui.Components.voip.CallSwipeView;
import ir.mehdi.ui.Components.voip.CheckableImageView;
import ir.mehdi.ui.Components.voip.FabBackgroundDrawable;
import ir.mehdi.ui.Components.voip.VoIPHelper;

import java.io.ByteArrayOutputStream;

public class VoIPActivity extends Activity implements VoIPService.StateListener, NotificationCenter.NotificationCenterDelegate
{

    private static final String TAG = "tg-voip-ui";
    private TextView stateText, nameText, stateText2;
    private TextView durationText;
    private View endBtn, acceptBtn, declineBtn, endBtnIcon, cancelBtn;
    private CheckableImageView spkToggle, micToggle;
    private ImageView chatBtn;
    private FabBackgroundDrawable endBtnBg;
    private CallSwipeView acceptSwipe, declineSwipe;
    private LinearLayout swipeViewsWrap;
    private BackupImageView photoView;
    private boolean isIncomingWaiting;
    private boolean firstStateChange = true;
    private Animator currentDeclineAnim, currentAcceptAnim, textChangingAnim;
    private TLRPC.User user;
    private boolean didAcceptFromHere = false;
    private int callState;
    private TextAlphaSpan[] ellSpans;
    private AnimatorSet ellAnimator;
    private String lastStateText;
    private ImageView[] keyEmojiViews=new ImageView[4];
    private boolean keyEmojiVisible;
	private AnimatorSet emojiAnimator;
    private TextView hintTextView;
    private Animator tooltipAnim;
    private Runnable tooltipHider;
	private LinearLayout emojiWrap;
    boolean emojiTooltipVisible;
    boolean emojiExpanded;
	private Bitmap blurredPhoto1, blurredPhoto2;
    private ImageView blurOverlayView1, blurOverlayView2;
    private TextView emojiExpandedText;
    private FrameLayout content;
    private boolean retrying;
    private AnimatorSet retryAnim;

    private int audioBitrate = 25;
    private int packetLossPercent = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        super.onCreate(savedInstanceState);

        if (VoIPService.getSharedInstance() == null) {
            finish();
            return;
        }

        if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) < Configuration.SCREENLAYOUT_SIZE_LARGE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        View contentView;
        setContentView(contentView = createContentView());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            //getWindow().setStatusBarColor(0x88000000);
            getWindow().setStatusBarColor(0xFF000000);
            //getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        user = VoIPService.getSharedInstance().getUser();
        if (user.photo != null) {
            photoView.getImageReceiver().setDelegate(new ImageReceiver.ImageReceiverDelegate(){
                @Override
                public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb){
                    Bitmap bmp=imageReceiver.getBitmap();
                    if(bmp!=null){
                        updateBlurredPhotos(bmp);
                    }
                }
            });
            photoView.setImage(user.photo.photo_big, null, new ColorDrawable(0xFF000000));
        } else {
            photoView.setVisibility(View.GONE);
            contentView.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1b354e, 0xFF255b7d}));
        }

        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        nameText.setOnClickListener(new View.OnClickListener() {
            private int tapCount=0;
            @Override
            public void onClick(View v) {
                if(BuildVars.DEBUG_VERSION || tapCount==9){
                    showDebugAlert();
                    tapCount=0;
                }else{
                    tapCount++;
                }
            }
        });
        /*nameText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showDebugCtlAlert();
                return true;
            }
        });*/

        endBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endBtn.setEnabled(false);
                if(retrying){
                    Intent intent = new Intent(VoIPActivity.this, VoIPService.class);
                    intent.putExtra("user_id", user.id);
                    intent.putExtra("is_outgoing", true);
                    intent.putExtra("start_incall_activity", false);
                    startService(intent);
                    hideRetry();
                    endBtn.postDelayed(new Runnable(){
                        @Override
                        public void run(){
                            if(VoIPService.getSharedInstance()==null && !isFinishing()){
                                endBtn.postDelayed(this, 100);
                                return;
                            }
                            if(VoIPService.getSharedInstance()!=null)
								VoIPService.getSharedInstance().registerStateListener(VoIPActivity.this);
                        }
                    }, 100);
                    return;
                }
                if (VoIPService.getSharedInstance() != null)
                    VoIPService.getSharedInstance().hangUp();
            }
        });

        spkToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
				VoIPService svc=VoIPService.getSharedInstance();
                if (svc == null)
                    return;
                if(svc.isBluetoothHeadsetConnected() && svc.hasEarpiece()){
                    BottomSheet.Builder bldr=new BottomSheet.Builder(VoIPActivity.this)
                            .setItems(new CharSequence[]{LocaleController.getString("VoipAudioRoutingBluetooth", R.string.VoipAudioRoutingBluetooth),
                                    LocaleController.getString("VoipAudioRoutingEarpiece", R.string.VoipAudioRoutingEarpiece),
                                    LocaleController.getString("VoipAudioRoutingSpeaker", R.string.VoipAudioRoutingSpeaker)},
                                    new int[]{R.drawable.ic_bluetooth_white_24dp,
                                            R.drawable.ic_phone_in_talk_white_24dp,
                                            R.drawable.ic_volume_up_white_24dp}, new DialogInterface.OnClickListener(){
                                @Override
                                public void onClick(DialogInterface dialog, int which){
                                    AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
                                    if(VoIPService.getSharedInstance()==null)
                                        return;
                                    switch(which){
                                        case 0:
                                            am.setBluetoothScoOn(true);
                                            am.setSpeakerphoneOn(false);
                                            break;
                                        case 1:
                                            am.setBluetoothScoOn(false);
                                            am.setSpeakerphoneOn(false);
                                            break;
                                        case 2:
                                            am.setBluetoothScoOn(false);
                                            am.setSpeakerphoneOn(true);
                                            break;
                                    }
                                    onAudioSettingsChanged();
                                }
                            });
                    bldr.show();
                    return;
                }
                boolean checked = !spkToggle.isChecked();
                spkToggle.setChecked(checked);
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if(svc.hasEarpiece()){
                    am.setSpeakerphoneOn(checked);
                }else{
					am.setBluetoothScoOn(checked);
                }
            }
        });
        micToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (VoIPService.getSharedInstance() == null) {
                    finish();
                    return;
                }
                boolean checked = !micToggle.isChecked();
                micToggle.setChecked(checked);
                VoIPService.getSharedInstance().setMicMute(checked);
            }
        });
        chatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
                intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
                intent.setFlags(32768);
                intent.putExtra("userId", user.id);
                startActivity(intent);
                finish();
            }
        });

        spkToggle.setChecked(((AudioManager) getSystemService(AUDIO_SERVICE)).isSpeakerphoneOn());
        micToggle.setChecked(VoIPService.getSharedInstance().isMicMute());

        onAudioSettingsChanged();

        nameText.setText(ContactsController.formatName(user.first_name, user.last_name));

        VoIPService.getSharedInstance().registerStateListener(this);

        acceptSwipe.setListener(new CallSwipeView.Listener() {
            @Override
            public void onDragComplete() {
                acceptSwipe.setEnabled(false);
                declineSwipe.setEnabled(false);
                if(VoIPService.getSharedInstance()==null){
					finish();
                    return;
                }
                didAcceptFromHere = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                } else {
                    VoIPService.getSharedInstance().acceptIncomingCall();
                    callAccepted();
                }
            }

            @Override
            public void onDragStart() {
                if (currentDeclineAnim != null) {
                    currentDeclineAnim.cancel();
                }
                AnimatorSet set = new AnimatorSet();
                set.playTogether(
                        ObjectAnimator.ofFloat(declineSwipe, "alpha", .2f),
                        ObjectAnimator.ofFloat(declineBtn, "alpha", .2f)
                );
                set.setDuration(200);
                set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        currentDeclineAnim = null;
                    }
                });
                currentDeclineAnim = set;
                set.start();
                declineSwipe.stopAnimatingArrows();
            }

            @Override
            public void onDragCancel() {
                if (currentDeclineAnim != null) {
                    currentDeclineAnim.cancel();
                }
                AnimatorSet set = new AnimatorSet();
                set.playTogether(
                        ObjectAnimator.ofFloat(declineSwipe, "alpha", 1),
                        ObjectAnimator.ofFloat(declineBtn, "alpha", 1)
                );
                set.setDuration(200);
                set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        currentDeclineAnim = null;
                    }
                });
                currentDeclineAnim = set;
                set.start();
                declineSwipe.startAnimatingArrows();
            }
        });
        declineSwipe.setListener(new CallSwipeView.Listener() {
            @Override
            public void onDragComplete() {
                acceptSwipe.setEnabled(false);
                declineSwipe.setEnabled(false);
                if(VoIPService.getSharedInstance()!=null)
					VoIPService.getSharedInstance().declineIncomingCall(VoIPService.DISCARD_REASON_LINE_BUSY, null);
                else
                    finish();
            }

            @Override
            public void onDragStart() {
                if (currentAcceptAnim != null) {
                    currentAcceptAnim.cancel();
                }
                AnimatorSet set = new AnimatorSet();
                set.playTogether(
                        ObjectAnimator.ofFloat(acceptSwipe, "alpha", .2f),
                        ObjectAnimator.ofFloat(acceptBtn, "alpha", .2f)
                );
                set.setDuration(200);
                set.setInterpolator(new DecelerateInterpolator());
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        currentAcceptAnim = null;
                    }
                });
                currentAcceptAnim = set;
                set.start();
                acceptSwipe.stopAnimatingArrows();
            }

            @Override
            public void onDragCancel() {
                if (currentAcceptAnim != null) {
                    currentAcceptAnim.cancel();
                }
                AnimatorSet set = new AnimatorSet();
                set.playTogether(
                        ObjectAnimator.ofFloat(acceptSwipe, "alpha", 1),
                        ObjectAnimator.ofFloat(acceptBtn, "alpha", 1)
                );
                set.setDuration(200);
                set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        currentAcceptAnim = null;
                    }
                });
                currentAcceptAnim = set;
                set.start();
                acceptSwipe.startAnimatingArrows();
            }
        });
        cancelBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                finish();
            }
        });
        getWindow().getDecorView().setKeepScreenOn(true);

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeInCallActivity);
        hintTextView.setText(LocaleController.formatString("CallEmojiKeyTooltip", R.string.CallEmojiKeyTooltip, user.first_name));
        emojiExpandedText.setText(LocaleController.formatString("CallEmojiKeyTooltip", R.string.CallEmojiKeyTooltip, user.first_name));
    }

    private View createContentView(){
        FrameLayout content=new FrameLayout(this);
        content.setBackgroundColor(0);

        BackupImageView photo=new BackupImageView(this){
            private Drawable topGradient=getResources().getDrawable(R.drawable.gradient_top);
            private Drawable bottomGradient=getResources().getDrawable(R.drawable.gradient_bottom);
            private Paint paint=new Paint();

            @Override
            protected void onDraw(Canvas canvas){
                super.onDraw(canvas);
                paint.setColor(0x4C000000);
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
                topGradient.setBounds(0, 0, getWidth(), AndroidUtilities.dp(170));
                topGradient.setAlpha(128);
                topGradient.draw(canvas);
                bottomGradient.setBounds(0, getHeight()-AndroidUtilities.dp(220), getWidth(), getHeight());
                bottomGradient.setAlpha(178);
                bottomGradient.draw(canvas);
            }
        };
        content.addView(photoView=photo);
        blurOverlayView1=new ImageView(this);
        blurOverlayView1.setScaleType(ImageView.ScaleType.CENTER_CROP);
		blurOverlayView1.setAlpha(0f);
        content.addView(blurOverlayView1);
        blurOverlayView2=new ImageView(this);
        blurOverlayView2.setScaleType(ImageView.ScaleType.CENTER_CROP);
        blurOverlayView2.setAlpha(0f);
        content.addView(blurOverlayView2);

        TextView branding=new TextView(this);
        branding.setTextColor(0xCCFFFFFF);
        branding.setText(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding));
        Drawable logo=getResources().getDrawable(R.drawable.notification).mutate();
        logo.setAlpha(0xCC);
        logo.setBounds(0, 0, AndroidUtilities.dp(15), AndroidUtilities.dp(15));
        branding.setCompoundDrawables(LocaleController.isRTL ? null : logo, null, LocaleController.isRTL ? logo : null, null);
        branding.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        branding.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        branding.setCompoundDrawablePadding(AndroidUtilities.dp(5));
        branding.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        content.addView(branding, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP|Gravity.LEFT, 18, 18, 18, 0));

        TextView name=new TextView(this);
        name.setSingleLine();
        name.setTextColor(0xFFFFFFFF);
        name.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf")); // telegraf
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        name.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        name.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        content.addView(nameText=name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP|Gravity.LEFT, 18, 43, 18, 0));

        TextView state=new TextView(this);
        state.setTextColor(0xCCFFFFFF);
        state.setSingleLine();
        state.setEllipsize(TextUtils.TruncateAt.END);
        state.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        state.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        state.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        state.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        //state.setAllCaps(true);
        content.addView(stateText=state, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP|Gravity.LEFT, 18, 98, 18, 0));
        durationText=state;

        state=new TextView(this);
        state.setTextColor(0xCCFFFFFF);
        state.setSingleLine();
        state.setEllipsize(TextUtils.TruncateAt.END);
        state.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        state.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        state.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        state.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        //state.setAllCaps(true);
        state.setVisibility(View.GONE);
        content.addView(stateText2=state, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP|Gravity.LEFT, 18, 98, 18, 0));

        ellSpans=new TextAlphaSpan[]{new TextAlphaSpan(), new TextAlphaSpan(), new TextAlphaSpan()};

        CheckableImageView mic=new CheckableImageView(this);
        mic.setBackgroundResource(R.drawable.bg_voip_icon_btn);
        Drawable micIcon=getResources().getDrawable(R.drawable.ic_mic_off_white_24dp).mutate();
        micIcon.setAlpha(204);
        mic.setImageDrawable(micIcon);
        mic.setScaleType(ImageView.ScaleType.CENTER);
        content.addView(micToggle=mic, LayoutHelper.createFrame(38, 38, Gravity.BOTTOM|Gravity.LEFT, 16, 0, 0, 10));

        CheckableImageView speaker=new CheckableImageView(this);
        speaker.setBackgroundResource(R.drawable.bg_voip_icon_btn);
        Drawable speakerIcon=getResources().getDrawable(R.drawable.ic_volume_up_white_24dp).mutate();
        speakerIcon.setAlpha(204);
        speaker.setImageDrawable(speakerIcon);
        speaker.setScaleType(ImageView.ScaleType.CENTER);
        content.addView(spkToggle=speaker, LayoutHelper.createFrame(38, 38, Gravity.BOTTOM|Gravity.RIGHT, 0, 0, 16, 10));

        ImageView chat=new ImageView(this);
        Drawable chatIcon=getResources().getDrawable(R.drawable.ic_chat_bubble_white_24dp).mutate();
        chatIcon.setAlpha(204);
        chat.setImageDrawable(chatIcon);
        chat.setScaleType(ImageView.ScaleType.CENTER);
        content.addView(chatBtn=chat, LayoutHelper.createFrame(38, 38, Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        LinearLayout swipesWrap=new LinearLayout(this);
        swipesWrap.setOrientation(LinearLayout.HORIZONTAL);

        CallSwipeView acceptSwipe=new CallSwipeView(this);
        acceptSwipe.setColor(0xFF45bc4d);
        swipesWrap.addView(this.acceptSwipe=acceptSwipe, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 70, 1f, 4, 4, -35, 4));

        CallSwipeView declineSwipe=new CallSwipeView(this);
        declineSwipe.setColor(0xFFe61e44);
        swipesWrap.addView(this.declineSwipe=declineSwipe, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 70, 1f, -35, 4, 4, 4));

        content.addView(swipeViewsWrap=swipesWrap, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 20, 0, 20, 68));

        ImageView acceptBtn=new ImageView(this);
        FabBackgroundDrawable acceptBtnBg=new FabBackgroundDrawable();
        acceptBtnBg.setColor(0xFF45bc4d);
        acceptBtn.setBackgroundDrawable(acceptBtnBg);
        acceptBtn.setImageResource(R.drawable.ic_call_end_white_36dp);
        acceptBtn.setScaleType(ImageView.ScaleType.MATRIX);
        Matrix matrix=new Matrix();
        matrix.setTranslate(AndroidUtilities.dp(17), AndroidUtilities.dp(17));
        matrix.postRotate(-135, AndroidUtilities.dp(35), AndroidUtilities.dp(35));
        acceptBtn.setImageMatrix(matrix);
        content.addView(this.acceptBtn=acceptBtn, LayoutHelper.createFrame(78, 78, Gravity.BOTTOM|Gravity.LEFT, 20, 0, 0, 68));

        ImageView declineBtn=new ImageView(this);
        FabBackgroundDrawable rejectBtnBg=new FabBackgroundDrawable();
        rejectBtnBg.setColor(0xFFe61e44);
        declineBtn.setBackgroundDrawable(rejectBtnBg);
        declineBtn.setImageResource(R.drawable.ic_call_end_white_36dp);
        declineBtn.setScaleType(ImageView.ScaleType.CENTER);
        content.addView(this.declineBtn=declineBtn, LayoutHelper.createFrame(78, 78, Gravity.BOTTOM|Gravity.RIGHT, 0, 0, 20, 68));

        acceptSwipe.setViewToDrag(acceptBtn, false);
        declineSwipe.setViewToDrag(declineBtn, true);

        FrameLayout end=new FrameLayout(this);
        FabBackgroundDrawable endBtnBg=new FabBackgroundDrawable();
        endBtnBg.setColor(0xFFe61e44);
        end.setBackgroundDrawable(this.endBtnBg=endBtnBg);
        ImageView endInner=new ImageView(this);
        endInner.setImageResource(R.drawable.ic_call_end_white_36dp);
        endInner.setScaleType(ImageView.ScaleType.CENTER);
        end.addView(endBtnIcon=endInner, LayoutHelper.createFrame(70, 70));
        end.setForeground(getResources().getDrawable(R.drawable.fab_highlight_dark));
        content.addView(endBtn=end, LayoutHelper.createFrame(78, 78, Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, 0, 0, 0, 68));

        ImageView cancelBtn=new ImageView(this);
        FabBackgroundDrawable cancelBtnBg=new FabBackgroundDrawable();
        cancelBtnBg.setColor(0xFFFFFFFF);
        cancelBtn.setBackgroundDrawable(cancelBtnBg);
        cancelBtn.setImageResource(R.drawable.edit_cancel);
        cancelBtn.setColorFilter(0x89000000);
        cancelBtn.setScaleType(ImageView.ScaleType.CENTER);
        cancelBtn.setVisibility(View.GONE);
        content.addView(this.cancelBtn=cancelBtn, LayoutHelper.createFrame(78, 78, Gravity.BOTTOM|Gravity.LEFT, 52, 0, 0, 68));
        

		emojiWrap=new LinearLayout(this);
        emojiWrap.setOrientation(LinearLayout.HORIZONTAL);
        emojiWrap.setClipToPadding(false);
        emojiWrap.setPivotX(0);
        emojiWrap.setPivotY(0);
		emojiWrap.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
        for(int i=0;i<4;i++){
            ImageView emoji=new ImageView(this);
            emoji.setScaleType(ImageView.ScaleType.FIT_XY);
            emojiWrap.addView(emoji, LayoutHelper.createLinear(22, 22, i==0 ? 0 : 4, 0, 0, 0));
            keyEmojiViews[i]=emoji;
        }
        emojiWrap.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(emojiTooltipVisible){
                    setEmojiTooltipVisible(false);
                    if(tooltipHider!=null){
                        hintTextView.removeCallbacks(tooltipHider);
                        tooltipHider=null;
                    }
                }
                setEmojiExpanded(!emojiExpanded);
            }
        });
        //keyEmojiText=new TextView(this);
        //keyEmojiText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        content.addView(emojiWrap, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP|Gravity.RIGHT));
        emojiWrap.setOnLongClickListener(new View.OnLongClickListener(){
            @Override
            public boolean onLongClick(View v){
                if(emojiExpanded)
                    return false;
                if(tooltipHider!=null){
                    hintTextView.removeCallbacks(tooltipHider);
                    tooltipHider=null;
                }
                setEmojiTooltipVisible(!emojiTooltipVisible);
                if(emojiTooltipVisible){
                    hintTextView.postDelayed(tooltipHider=new Runnable(){
                        @Override
                        public void run(){
                            tooltipHider=null;
                            setEmojiTooltipVisible(false);
                        }
                    }, 5000);
                }
                return true;
            }
        });
        emojiExpandedText=new TextView(this);
        emojiExpandedText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emojiExpandedText.setTextColor(0xFFFFFFFF);
        emojiExpandedText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf")); // telegraf
        emojiExpandedText.setGravity(Gravity.CENTER);
		emojiExpandedText.setAlpha(0);
        content.addView(emojiExpandedText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 10, 32, 10, 0));

        hintTextView = new CorrectlyMeasuringTextView(this);
        hintTextView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), 0xf2333333));
        hintTextView.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
        hintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hintTextView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        hintTextView.setGravity(Gravity.CENTER);
        hintTextView.setMaxWidth(AndroidUtilities.dp(300));
        hintTextView.setAlpha(0.0f);
        content.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 42, 10, 0));

        int ellMaxAlpha=stateText.getPaint().getAlpha();
        ellAnimator=new AnimatorSet();
        ellAnimator.playTogether(
                createAlphaAnimator(ellSpans[0], 0, ellMaxAlpha, 0, 300),
                createAlphaAnimator(ellSpans[1], 0, ellMaxAlpha, 150, 300),
                createAlphaAnimator(ellSpans[2], 0, ellMaxAlpha, 300, 300),
                createAlphaAnimator(ellSpans[0], ellMaxAlpha, 0, 1000, 400),
                createAlphaAnimator(ellSpans[1], ellMaxAlpha, 0, 1000, 400),
                createAlphaAnimator(ellSpans[2], ellMaxAlpha, 0, 1000, 400)
        );
        ellAnimator.addListener(new AnimatorListenerAdapter(){
			private Runnable restarter=new Runnable(){
                @Override
                public void run(){
                    if(!isFinishing())
                        ellAnimator.start();
                }
            };
            @Override
            public void onAnimationEnd(Animator animation){
                if(!isFinishing()){
                    VoIPActivity.this.content.postDelayed(restarter, 300);
                }
            }
        });
		content.setClipChildren(false);
        this.content=content;

        return content;
    }

	@SuppressLint("ObjectAnimatorBinding")
    private ObjectAnimator createAlphaAnimator(Object target, int startVal, int endVal, int startDelay, int duration){
        ObjectAnimator a=ObjectAnimator.ofInt(target, "alpha", startVal, endVal);
        a.setDuration(duration);
        a.setStartDelay(startDelay);
        a.setInterpolator(CubicBezierInterpolator.DEFAULT);
        return a;
    }

    @Override
    protected void onDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeInCallActivity);
        if (VoIPService.getSharedInstance() != null) {
            VoIPService.getSharedInstance().unregisterStateListener(this);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if(emojiExpanded){
            setEmojiExpanded(false);
            return;
        }
        if (!isIncomingWaiting) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (VoIPService.getSharedInstance() != null)
            VoIPService.getSharedInstance().onUIForegroundStateChanged(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(retrying)
            finish();
        if (VoIPService.getSharedInstance() != null)
            VoIPService.getSharedInstance().onUIForegroundStateChanged(false);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 101) {
            if (grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                VoIPService.getSharedInstance().acceptIncomingCall();
                callAccepted();
            } else {
                if(!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)){
                    VoIPService.getSharedInstance().declineIncomingCall();
                    VoIPHelper.permissionDenied(this, new Runnable(){
                        @Override
                        public void run(){
                            finish();
                        }
                    });
                    return;
                }
                acceptSwipe.reset();
            }
        }

    }

    private void updateKeyView() {
        if(VoIPService.getSharedInstance()==null)
            return;
        IdenticonDrawable img = new IdenticonDrawable();
        img.setColors(new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x99FFFFFF, 0x33FFFFFF});
        TLRPC.EncryptedChat encryptedChat = new TLRPC.EncryptedChat();
        try{
            ByteArrayOutputStream buf=new ByteArrayOutputStream();
            buf.write(VoIPService.getSharedInstance().getEncryptionKey());
			buf.write(VoIPService.getSharedInstance().getGA());
            encryptedChat.auth_key = buf.toByteArray();
        }catch(Exception checkedExceptionsAreBad){}
        byte[] sha256 = Utilities.computeSHA256(encryptedChat.auth_key, 0, encryptedChat.auth_key.length);
        String[] emoji= EncryptionKeyEmojifier.emojifyForCall(sha256);
        //keyEmojiText.setText(Emoji.replaceEmoji(TextUtils.join(" ", emoji), keyEmojiText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(32), false));
        for(int i=0;i<4;i++){
            Drawable drawable=Emoji.getEmojiDrawable(emoji[i]);
            drawable.setBounds(0, 0, AndroidUtilities.dp(22), AndroidUtilities.dp(22));
            keyEmojiViews[i].setImageDrawable(drawable);
        }
    }

    private void showDebugAlert() {
        if(VoIPService.getSharedInstance()==null)
            return;
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("libtgvoip v" + VoIPController.getVersion() + " debug")
                .setMessage(VoIPService.getSharedInstance().getDebugString())
                .setPositiveButton("Close", null)
                .create();
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                if (!dlg.isShowing() || isFinishing() || VoIPService.getSharedInstance() == null) {
                    return;
                }
                dlg.setMessage(VoIPService.getSharedInstance().getDebugString());
                dlg.getWindow().getDecorView().postDelayed(this, 500);
            }
        };
        dlg.show();
        dlg.getWindow().getDecorView().postDelayed(r, 500);
    }

    private void showDebugCtlAlert() {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"Set audio bitrate", "Set expect packet loss %", "Disable p2p", "Enable p2p", "Disable AEC", "Enable AEC"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                showNumberPickerDialog(8, 32, audioBitrate, "Audio bitrate (kbit/s)", new NumberPicker.OnValueChangeListener() {
                                    @Override
                                    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                                        audioBitrate = newVal;
                                        VoIPService.getSharedInstance().debugCtl(1, newVal * 1000);
                                    }
                                });
                                break;
                            case 1:
                                showNumberPickerDialog(0, 100, packetLossPercent, "Expected packet loss %", new NumberPicker.OnValueChangeListener() {
                                    @Override
                                    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                                        packetLossPercent = newVal;
                                        VoIPService.getSharedInstance().debugCtl(2, newVal);
                                    }
                                });
                                break;
                            case 2:
                                VoIPService.getSharedInstance().debugCtl(3, 0);
                                break;
                            case 3:
                                VoIPService.getSharedInstance().debugCtl(3, 1);
                                break;
                            case 4:
                                VoIPService.getSharedInstance().debugCtl(4, 0);
                                break;
                            case 5:
                                VoIPService.getSharedInstance().debugCtl(4, 1);
                                break;
                        }
                    }
                })
                .show();
    }

    private void showNumberPickerDialog(int min, int max, int value, String title, NumberPicker.OnValueChangeListener listener) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setOnValueChangedListener(listener);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(picker)
                .setPositiveButton("Done", null)
                .show();
    }

    private void startUpdatingCallDuration() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || VoIPService.getSharedInstance() == null) {
                    return;
                }
                if(callState==VoIPService.STATE_ESTABLISHED){
                    long duration=VoIPService.getSharedInstance().getCallDuration()/1000;
                    durationText.setText(duration>3600 ? String.format("%d:%02d:%02d", duration/3600, duration%3600/60, duration%60) : String.format("%d:%02d", duration/60, duration%60));
                    durationText.postDelayed(this, 500);
                }
            }
        };
        r.run();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isIncomingWaiting && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            if(VoIPService.getSharedInstance()!=null)
				VoIPService.getSharedInstance().stopRinging();
            else
                finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void callAccepted() {
        endBtn.setVisibility(View.VISIBLE);
        micToggle.setVisibility(View.VISIBLE);
        if(VoIPService.getSharedInstance().hasEarpiece())
            spkToggle.setVisibility(View.VISIBLE);
        chatBtn.setVisibility(View.VISIBLE);
        if (didAcceptFromHere) {
            acceptBtn.setVisibility(View.GONE);
            ObjectAnimator colorAnim;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                colorAnim = ObjectAnimator.ofArgb(endBtnBg, "color", 0xFF45bc4d, 0xFFe61e44);
            } else {
                colorAnim = ObjectAnimator.ofInt(endBtnBg, "color", 0xFF45bc4d, 0xFFe61e44);
                colorAnim.setEvaluator(new ArgbEvaluator());
            }
            AnimatorSet set = new AnimatorSet();
            AnimatorSet decSet = new AnimatorSet();
            decSet.playTogether(
                    ObjectAnimator.ofFloat(micToggle, "alpha", 0, 1),
                    ObjectAnimator.ofFloat(spkToggle, "alpha", 0, 1),
                    ObjectAnimator.ofFloat(chatBtn, "alpha", 0, 1),
                    ObjectAnimator.ofFloat(endBtnIcon, "rotation", -135, 0),
                    colorAnim
            );
            decSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            decSet.setDuration(500);
            AnimatorSet accSet = new AnimatorSet();
            accSet.playTogether(
                    ObjectAnimator.ofFloat(swipeViewsWrap, "alpha", 1, 0),
                    ObjectAnimator.ofFloat(declineBtn, "alpha", 0)
            );
            accSet.setInterpolator(CubicBezierInterpolator.EASE_IN);
            accSet.setDuration(125);
            set.playTogether(
                    decSet,
                    accSet
            );
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    swipeViewsWrap.setVisibility(View.GONE);
                    declineBtn.setVisibility(View.GONE);
                }
            });
            set.start();
        } else {
            AnimatorSet set = new AnimatorSet();
            AnimatorSet decSet = new AnimatorSet();
            decSet.playTogether(
                    ObjectAnimator.ofFloat(micToggle, "alpha", 0, 1),
                    ObjectAnimator.ofFloat(spkToggle, "alpha", 0, 1),
                    ObjectAnimator.ofFloat(chatBtn, "alpha", 0, 1)
            );
            decSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            decSet.setDuration(500);
            AnimatorSet accSet = new AnimatorSet();
            accSet.playTogether(
                    ObjectAnimator.ofFloat(swipeViewsWrap, "alpha", 1, 0),
                    ObjectAnimator.ofFloat(declineBtn, "alpha", 0),
                    ObjectAnimator.ofFloat(acceptBtn, "alpha", 0)
            );
            accSet.setInterpolator(CubicBezierInterpolator.EASE_IN);
            accSet.setDuration(125);
            set.playTogether(
                    decSet,
                    accSet
            );
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    swipeViewsWrap.setVisibility(View.GONE);
                    declineBtn.setVisibility(View.GONE);
                    acceptBtn.setVisibility(View.GONE);
                }
            });
            set.start();
        }
    }

    private void showRetry(){
        if(retryAnim!=null)
            retryAnim.cancel();
        endBtn.setEnabled(false);
        retrying=true;
        cancelBtn.setVisibility(View.VISIBLE);
        cancelBtn.setAlpha(0);
        AnimatorSet set=new AnimatorSet();
        ObjectAnimator colorAnim;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            colorAnim = ObjectAnimator.ofArgb(endBtnBg, "color", 0xFFe61e44, 0xFF45bc4d);
        } else {
            colorAnim = ObjectAnimator.ofInt(endBtnBg, "color", 0xFFe61e44, 0xFF45bc4d);
            colorAnim.setEvaluator(new ArgbEvaluator());
        }
        set.playTogether(
                ObjectAnimator.ofFloat(cancelBtn, "alpha", 0, 1),
                ObjectAnimator.ofFloat(endBtn, "translationX", 0, content.getWidth()/2-AndroidUtilities.dp(52)-endBtn.getWidth()/2),
                colorAnim,
                ObjectAnimator.ofFloat(endBtnIcon, "rotation", 0, -135),
                ObjectAnimator.ofFloat(spkToggle, "alpha", 0),
                ObjectAnimator.ofFloat(micToggle, "alpha", 0),
                ObjectAnimator.ofFloat(chatBtn, "alpha", 0)
        );
        set.setStartDelay(200);
        set.setDuration(300);
        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
        set.addListener(new AnimatorListenerAdapter(){
            @Override
            public void onAnimationEnd(Animator animation){
                spkToggle.setVisibility(View.GONE);
                micToggle.setVisibility(View.GONE);
                chatBtn.setVisibility(View.GONE);
                retryAnim=null;
                endBtn.setEnabled(true);
            }
        });
		retryAnim=set;
        set.start();
    }

    private void hideRetry(){
        if(retryAnim!=null)
            retryAnim.cancel();
        retrying=false;
        spkToggle.setVisibility(View.VISIBLE);
        micToggle.setVisibility(View.VISIBLE);
        chatBtn.setVisibility(View.VISIBLE);
        ObjectAnimator colorAnim;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            colorAnim = ObjectAnimator.ofArgb(endBtnBg, "color", 0xFF45bc4d, 0xFFe61e44);
        } else {
            colorAnim = ObjectAnimator.ofInt(endBtnBg, "color", 0xFF45bc4d, 0xFFe61e44);
            colorAnim.setEvaluator(new ArgbEvaluator());
        }
        AnimatorSet set=new AnimatorSet();
        set.playTogether(
                colorAnim,
                ObjectAnimator.ofFloat(endBtnIcon, "rotation", -135, 0),
                ObjectAnimator.ofFloat(endBtn, "translationX", 0),
                ObjectAnimator.ofFloat(cancelBtn, "alpha", 0),
                ObjectAnimator.ofFloat(spkToggle, "alpha", 1),
                ObjectAnimator.ofFloat(micToggle, "alpha", 1),
                ObjectAnimator.ofFloat(chatBtn, "alpha", 1)
        );
        set.setStartDelay(200);
        set.setDuration(300);
        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
        set.addListener(new AnimatorListenerAdapter(){
            @Override
            public void onAnimationEnd(Animator animation){
                cancelBtn.setVisibility(View.GONE);
                endBtn.setEnabled(true);
				retryAnim=null;
            }
        });
        retryAnim=set;
        set.start();
    }

    @Override
    public void onStateChanged(final int state) {
        callState=state;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean wasFirstStateChange=firstStateChange;
                if (firstStateChange) {
                    if (isIncomingWaiting = state == VoIPService.STATE_WAITING_INCOMING) {
                        swipeViewsWrap.setVisibility(View.VISIBLE);
                        endBtn.setVisibility(View.GONE);
                        micToggle.setVisibility(View.GONE);
                        spkToggle.setVisibility(View.GONE);
                        chatBtn.setVisibility(View.GONE);
                        AndroidUtilities.runOnUIThread(new Runnable(){
                            @Override
                            public void run(){
                                acceptSwipe.startAnimatingArrows();
                                declineSwipe.startAnimatingArrows();
                            }
                        }, 500);
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                    } else {
                        swipeViewsWrap.setVisibility(View.GONE);
                        acceptBtn.setVisibility(View.GONE);
                        declineBtn.setVisibility(View.GONE);
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                    }
                    if (state != VoIPService.STATE_ESTABLISHED)
                        emojiWrap.setVisibility(View.GONE);
                    firstStateChange = false;
                }

                if (isIncomingWaiting && state != VoIPService.STATE_WAITING_INCOMING && state!=VoIPService.STATE_ENDED && state!=VoIPService.STATE_HANGING_UP) {
                    isIncomingWaiting = false;
                    if (!didAcceptFromHere)
                        callAccepted();
                }

                if (state == VoIPService.STATE_WAITING_INCOMING) {
                    setStateTextAnimated(LocaleController.getString("VoipIncoming", R.string.VoipIncoming), false);
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                } else if (state == VoIPService.STATE_WAIT_INIT || state == VoIPService.STATE_WAIT_INIT_ACK) {
                    setStateTextAnimated(LocaleController.getString("VoipConnecting", R.string.VoipConnecting), true);
                } else if (state == VoIPService.STATE_EXCHANGING_KEYS) {
                    setStateTextAnimated(LocaleController.getString("VoipExchangingKeys", R.string.VoipExchangingKeys), true);
                } else if (state == VoIPService.STATE_WAITING) {
                    setStateTextAnimated(LocaleController.getString("VoipWaiting", R.string.VoipWaiting), true);
                } else if (state == VoIPService.STATE_RINGING) {
                    setStateTextAnimated(LocaleController.getString("VoipRinging", R.string.VoipRinging), true);
                } else if (state == VoIPService.STATE_REQUESTING) {
                    setStateTextAnimated(LocaleController.getString("VoipRequesting", R.string.VoipRequesting), true);
                } else if (state == VoIPService.STATE_HANGING_UP) {
                    setStateTextAnimated(LocaleController.getString("VoipHangingUp", R.string.VoipHangingUp), true);
                } else if (state == VoIPService.STATE_ENDED) {
                    setStateTextAnimated(LocaleController.getString("VoipCallEnded", R.string.VoipCallEnded), false);
                    stateText.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }, 200);
                } else if (state == VoIPService.STATE_BUSY) {
                    //endBtn.setEnabled(false);
                    setStateTextAnimated(LocaleController.getString("VoipBusy", R.string.VoipBusy), false);
                    /*stateText.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }, 2000);*/
                    showRetry();
                } else if (state == VoIPService.STATE_ESTABLISHED) {
                    if(!wasFirstStateChange){
                        int count=getSharedPreferences("mainconfig", MODE_PRIVATE).getInt("call_emoji_tooltip_count", 0);
                        if(count<3){
                            setEmojiTooltipVisible(true);
                            hintTextView.postDelayed(tooltipHider=new Runnable(){
                                @Override
                                public void run(){
                                    tooltipHider=null;
                                    setEmojiTooltipVisible(false);
                                }
                            }, 5000);
                            getSharedPreferences("mainconfig", MODE_PRIVATE).edit().putInt("call_emoji_tooltip_count", count+1).apply();
                        }
                    }
                    setStateTextAnimated("0:00", false);
                    startUpdatingCallDuration();
                    updateKeyView();
                    if (emojiWrap.getVisibility() != View.VISIBLE) {
                        emojiWrap.setVisibility(View.VISIBLE);
                        emojiWrap.setAlpha(0f);
                        emojiWrap.animate().alpha(1).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
                    }
                } else if (state == VoIPService.STATE_FAILED) {
                    setStateTextAnimated(LocaleController.getString("VoipFailed", R.string.VoipFailed), false);
                    int lastError=VoIPService.getSharedInstance()!=null ? VoIPService.getSharedInstance().getLastError() : VoIPController.ERROR_UNKNOWN;
                    if (lastError== VoIPController.ERROR_INCOMPATIBLE) {
                        showErrorDialog(AndroidUtilities.replaceTags(LocaleController.formatString("VoipPeerIncompatible", R.string.VoipPeerIncompatible,
                                        ContactsController.formatName(user.first_name, user.last_name))));
                    }else if (lastError== VoIPController.ERROR_PEER_OUTDATED) {
                        showErrorDialog(AndroidUtilities.replaceTags(LocaleController.formatString("VoipPeerOutdated", R.string.VoipPeerOutdated,
                                        ContactsController.formatName(user.first_name, user.last_name))));
                    }else if(lastError==VoIPController.ERROR_PRIVACY){
                        showErrorDialog(AndroidUtilities.replaceTags(LocaleController.formatString("CallNotAvailable", R.string.CallNotAvailable,
                                ContactsController.formatName(user.first_name, user.last_name))));
                    }else if(lastError==VoIPController.ERROR_AUDIO_IO){
                        showErrorDialog("Error initializing audio hardware");
                    }else if(lastError==VoIPController.ERROR_LOCALIZED){
                        finish();
                    } else {
                        stateText.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                finish();
                            }
                        }, 1000);
                    }
                }
            }
        });
    }

    private void showErrorDialog(CharSequence message){
        AlertDialog dlg = new AlertDialog.Builder(VoIPActivity.this)
                .setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
                .setMessage(message)
                .setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
                .show();
        dlg.setCanceledOnTouchOutside(true);
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });
	}

    @Override
    public void onAudioSettingsChanged() {
        if(VoIPService.getSharedInstance()==null)
            return;
        micToggle.setChecked(VoIPService.getSharedInstance().isMicMute());
        if(!VoIPService.getSharedInstance().hasEarpiece() && !VoIPService.getSharedInstance().isBluetoothHeadsetConnected()){
            spkToggle.setVisibility(View.INVISIBLE);
        }else{
            spkToggle.setVisibility(View.VISIBLE);
            AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
            if(!VoIPService.getSharedInstance().hasEarpiece()){
                spkToggle.setImageResource(R.drawable.ic_bluetooth_white_24dp);
                spkToggle.setChecked(am.isBluetoothScoOn());
            }else if(VoIPService.getSharedInstance().isBluetoothHeadsetConnected()){
                if(am.isBluetoothScoOn()){
                    spkToggle.setImageResource(R.drawable.ic_bluetooth_white_24dp);
                }else if(am.isSpeakerphoneOn()){
                    spkToggle.setImageResource(R.drawable.ic_volume_up_white_24dp);
                }else{
                    spkToggle.setImageResource(R.drawable.ic_phone_in_talk_white_24dp);
                }
                spkToggle.setChecked(false);
            }else{
                spkToggle.setImageResource(R.drawable.ic_volume_up_white_24dp);
                spkToggle.setChecked(am.isSpeakerphoneOn());
            }
        }
    }

    private void setStateTextAnimated(String _newText, boolean ellipsis){
        if(_newText.equals(lastStateText))
            return;
        lastStateText=_newText;
        if(textChangingAnim!=null)
            textChangingAnim.cancel();
        CharSequence newText;
        if(ellipsis){
            if(!ellAnimator.isRunning())
                ellAnimator.start();
            SpannableStringBuilder ssb=new SpannableStringBuilder(_newText.toUpperCase());
            for(TextAlphaSpan s:ellSpans)
                s.setAlpha(0);
            SpannableString ell=new SpannableString("...");
            ell.setSpan(ellSpans[0], 0, 1, 0);
            ell.setSpan(ellSpans[1], 1, 2, 0);
            ell.setSpan(ellSpans[2], 2, 3, 0);
            ssb.append(ell);
            newText=ssb;
        }else{
            if(ellAnimator.isRunning())
                ellAnimator.cancel();
            newText=_newText.toUpperCase();
        }
        stateText2.setText(newText);
        stateText2.setVisibility(View.VISIBLE);
        stateText.setPivotX(LocaleController.isRTL ? stateText.getWidth() : 0);
        stateText.setPivotY(stateText.getHeight()/2);
        stateText2.setPivotX(LocaleController.isRTL ? stateText.getWidth() : 0);
        stateText2.setPivotY(stateText.getHeight()/2);
        durationText=stateText2;
        AnimatorSet set=new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(stateText2, "alpha", 0, 1),
                ObjectAnimator.ofFloat(stateText2, "translationY", stateText.getHeight()/2, 0),
                ObjectAnimator.ofFloat(stateText2, "scaleX", 0.7f, 1),
                ObjectAnimator.ofFloat(stateText2, "scaleY", 0.7f, 1),
                ObjectAnimator.ofFloat(stateText, "alpha", 1, 0),
                ObjectAnimator.ofFloat(stateText, "translationY", 0, -stateText.getHeight()/2),
                ObjectAnimator.ofFloat(stateText, "scaleX", 1, 0.7f),
                ObjectAnimator.ofFloat(stateText, "scaleY", 1, 0.7f)
        );
        set.setDuration(200);
        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
        set.addListener(new AnimatorListenerAdapter(){
            @Override
            public void onAnimationEnd(Animator animation){
                textChangingAnim=null;
                stateText2.setVisibility(View.GONE);
                durationText=stateText;
                stateText.setTranslationY(0);
                stateText.setScaleX(1);
                stateText.setScaleY(1);
                stateText.setAlpha(1);
                stateText.setText(stateText2.getText());
            }
        });
        textChangingAnim=set;
        set.start();
    }

    @Override
    public void didReceivedNotification(int id, Object... args){
        if(id==NotificationCenter.emojiDidLoaded){
            for(ImageView iv:keyEmojiViews){
                iv.invalidate();
            }
        }
        if(id==NotificationCenter.closeInCallActivity){
            finish();
        }
    }

    private void setEmojiTooltipVisible(boolean visible){
        emojiTooltipVisible=visible;
        if(tooltipAnim!=null)
		    tooltipAnim.cancel();
		hintTextView.setVisibility(View.VISIBLE);
		ObjectAnimator oa=ObjectAnimator.ofFloat(hintTextView, "alpha", visible ? 1 : 0);
		oa.setDuration(300);
		oa.setInterpolator(CubicBezierInterpolator.DEFAULT);
		oa.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				tooltipAnim=null;
			}
		});
		tooltipAnim=oa;
		oa.start();
    }

    private void setEmojiExpanded(boolean expanded){
        if(emojiExpanded==expanded)
            return;
        emojiExpanded=expanded;
        if(emojiAnimator!=null)
            emojiAnimator.cancel();
        if(expanded){
            int[] loc={0, 0}, loc2={0, 0};
            emojiWrap.getLocationInWindow(loc);
            emojiExpandedText.getLocationInWindow(loc2);
            Rect rect=new Rect();
            getWindow().getDecorView().getGlobalVisibleRect(rect);
            int offsetY=loc2[1]-(loc[1]+emojiWrap.getHeight())-AndroidUtilities.dp(32)-emojiWrap.getHeight();
            int firstOffsetX=(rect.width()/2-Math.round(emojiWrap.getWidth()*2.5f)/2)-loc[0];
            AnimatorSet set=new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(emojiWrap, "translationY", offsetY),
                    ObjectAnimator.ofFloat(emojiWrap, "translationX", firstOffsetX),
                    ObjectAnimator.ofFloat(emojiWrap, "scaleX", 2.5f),
                    ObjectAnimator.ofFloat(emojiWrap, "scaleY", 2.5f),
                    ObjectAnimator.ofFloat(blurOverlayView1, "alpha", blurOverlayView1.getAlpha(), 1, 1),
                    ObjectAnimator.ofFloat(blurOverlayView2, "alpha", blurOverlayView2.getAlpha(), blurOverlayView2.getAlpha(), 1),
                    ObjectAnimator.ofFloat(emojiExpandedText, "alpha", 1)
            );
            set.setDuration(300);
            set.setInterpolator(CubicBezierInterpolator.DEFAULT);
            emojiAnimator=set;
            set.addListener(new AnimatorListenerAdapter(){
                @Override
                public void onAnimationEnd(Animator animation){
                    emojiAnimator=null;
                }
            });
            set.start();
        }else{
            AnimatorSet set=new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(emojiWrap, "translationX", 0),
                    ObjectAnimator.ofFloat(emojiWrap, "translationY", 0),
					ObjectAnimator.ofFloat(emojiWrap, "scaleX", 1),
                    ObjectAnimator.ofFloat(emojiWrap, "scaleY", 1),
                    ObjectAnimator.ofFloat(blurOverlayView1, "alpha", blurOverlayView1.getAlpha(), blurOverlayView1.getAlpha(), 0),
                    ObjectAnimator.ofFloat(blurOverlayView2, "alpha", blurOverlayView2.getAlpha(), 0, 0),
                    ObjectAnimator.ofFloat(emojiExpandedText, "alpha", 0)
            );
            set.setDuration(300);
            set.setInterpolator(CubicBezierInterpolator.DEFAULT);
            emojiAnimator=set;
            set.addListener(new AnimatorListenerAdapter(){
                @Override
                public void onAnimationEnd(Animator animation){
                    emojiAnimator=null;
                }
            });
            set.start();
        }
    }

    private void updateBlurredPhotos(final Bitmap src){
        new Thread(new Runnable(){
            @Override
            public void run(){
                Bitmap blur1=Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(blur1);
                canvas.drawBitmap(src, null, new Rect(0, 0, 150, 150), new Paint(Paint.FILTER_BITMAP_FLAG));
                Utilities.blurBitmap(blur1, 3, 0, blur1.getWidth(), blur1.getHeight(), blur1.getRowBytes());
                final Palette palette=Palette.from(src).generate();
                Paint paint=new Paint();
                paint.setColor((palette.getDarkMutedColor(0xFF547499) & 0x00FFFFFF) | 0x44000000);
                canvas.drawColor(0x26000000);
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
                Bitmap blur2=Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
				canvas=new Canvas(blur2);
                canvas.drawBitmap(src, null, new Rect(0, 0, 50, 50), new Paint(Paint.FILTER_BITMAP_FLAG));
                Utilities.blurBitmap(blur2, 3, 0, blur2.getWidth(), blur2.getHeight(), blur2.getRowBytes());
				paint.setAlpha(0x66);
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
				blurredPhoto1=blur1;
                blurredPhoto2=blur2;
                runOnUiThread(new Runnable(){
                    @Override
                    public void run(){
                        blurOverlayView1.setImageBitmap(blurredPhoto1);
                        blurOverlayView2.setImageBitmap(blurredPhoto2);
                    }
                });
            }
        }).start();
    }

    private class TextAlphaSpan extends CharacterStyle{
        private int alpha;

        public TextAlphaSpan(){
            this.alpha=0;
        }

        public int getAlpha(){
            return alpha;
        }

        public void setAlpha(int alpha){
            this.alpha=alpha;
            stateText.invalidate();
            stateText2.invalidate();
        }

        @Override
        public void updateDrawState(TextPaint tp){
            tp.setAlpha(alpha);
        }
    }
}
