/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package ir.mehdi.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import ir.mehdi.messenger.AndroidUtilities;
import ir.mehdi.messenger.ImageReceiver;
import ir.mehdi.messenger.LocaleController;
import ir.mehdi.messenger.MessageObject;
import ir.mehdi.messenger.FileLoader;
import ir.mehdi.messenger.FileLog;
import ir.mehdi.tgnet.TLRPC;
import ir.mehdi.messenger.UserConfig;
import ir.mehdi.ui.ActionBar.Theme;
import ir.mehdi.ui.PhotoViewer;
import ir.mehdi.ui.Components.AvatarDrawable;

public class ChatActionCell extends BaseCell {

    public interface ChatActionCellDelegate {
        void didClickedImage(ChatActionCell cell);
        void didLongPressed(ChatActionCell cell);
        void needOpenUserProfile(int uid);
        void didPressedBotButton(MessageObject messageObject, TLRPC.KeyboardButton button);
        void didPressedReplyMessage(ChatActionCell cell, int id);
    }

    private URLSpan pressedLink;

    private ImageReceiver  imageReceiver;
    private AvatarDrawable avatarDrawable;
    private StaticLayout   textLayout;
    private int textWidth = 0;
    private int textHeight = 0;
    private int textX = 0;
    private int textY = 0;
    private int textXLeft = 0;
    private int previousWidth = 0;
    private boolean imagePressed = false;

    private boolean hasReplyMessage;

    private MessageObject currentMessageObject;
    private int customDate;
    private CharSequence customText;

    private ChatActionCellDelegate delegate;

    public ChatActionCell(Context context) {
        super(context);
        imageReceiver = new ImageReceiver(this);
        imageReceiver.setRoundRadius(AndroidUtilities.dp(32));
        avatarDrawable = new AvatarDrawable();
    }

    public void setDelegate(ChatActionCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setCustomDate(int date) {
        if (customDate == date) {
            return;
        }
        CharSequence newText = LocaleController.formatDateChat(date);
        if (customText != null && TextUtils.equals(newText, customText)) {
            return;
        }
        previousWidth = 0;
        customDate = date;
        customText = newText;
        if (getMeasuredWidth() != 0) {
            createLayout(customText, getMeasuredWidth());
            invalidate();
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                requestLayout();
            }
        });
    }

    public void setMessageObject(MessageObject messageObject) {
        if (currentMessageObject == messageObject && (hasReplyMessage || messageObject.replyMessageObject == null)) {
            return;
        }
        currentMessageObject = messageObject;
        hasReplyMessage = messageObject.replyMessageObject != null;
        previousWidth = 0;
        if (currentMessageObject.type == 11) {
            int id = 0;
            if (messageObject.messageOwner.to_id != null) {
                if (messageObject.messageOwner.to_id.chat_id != 0) {
                    id = messageObject.messageOwner.to_id.chat_id;
                } else if (messageObject.messageOwner.to_id.channel_id != 0) {
                    id = messageObject.messageOwner.to_id.channel_id;
                } else {
                    id = messageObject.messageOwner.to_id.user_id;
                    if (id == UserConfig.getClientUserId()) {
                        id = messageObject.messageOwner.from_id;
                    }
                }
            }
            avatarDrawable.setInfo(id, null, null, false);
            if (currentMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                imageReceiver.setImage(currentMessageObject.messageOwner.action.newUserPhoto.photo_small, "50_50", avatarDrawable, null, false);
            } else {
                TLRPC.PhotoSize photo = FileLoader.getClosestPhotoSizeWithSize(currentMessageObject.photoThumbs, AndroidUtilities.dp(64));
                if (photo != null) {
                    imageReceiver.setImage(photo.location, "50_50", avatarDrawable, null, false);
                } else {
                    imageReceiver.setImageBitmap(avatarDrawable);
                }
            }
            imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(currentMessageObject), false);
        } else {
            imageReceiver.setImageBitmap((Bitmap) null);
        }
        requestLayout();
    }

    public MessageObject getMessageObject() {
        return currentMessageObject;
    }

    public ImageReceiver getPhotoImage() {
        return imageReceiver;
    }

    @Override
    protected void onLongPress() {
        if (delegate != null) {
            delegate.didLongPressed(this);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentMessageObject == null) {
            return super.onTouchEvent(event);
        }
        float x = event.getX();
        float y = event.getY();

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (delegate != null) {
                if (currentMessageObject.type == 11 && imageReceiver.isInsideImage(x, y)) {
                    imagePressed = true;
                    result = true;
                }
                if (result) {
                    startCheckLongPress();
                }
            }
        } else {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }
            if (imagePressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    imagePressed = false;
                    if (delegate != null) {
                        delegate.didClickedImage(this);
                        playSoundEffect(SoundEffectConstants.CLICK);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    imagePressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!imageReceiver.isInsideImage(x, y)) {
                        imagePressed = false;
                    }
                }
            }
        }
        if (!result) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || pressedLink != null && event.getAction() == MotionEvent.ACTION_UP) {
                if (x >= textX && y >= textY && x <= textX + textWidth && y <= textY + textHeight) {
                    y -= textY;
                    x -= textXLeft;

                    final int line = textLayout.getLineForVertical((int)y);
                    final int off = textLayout.getOffsetForHorizontal(line, x);
                    final float left = textLayout.getLineLeft(line);
                    if (left <= x && left + textLayout.getLineWidth(line) >= x && currentMessageObject.messageText instanceof Spannable) {
                        Spannable buffer = (Spannable) currentMessageObject.messageText;
                        URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);

                        if (link.length != 0) {
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                pressedLink = link[0];
                                result = true;
                            } else {
                                if (link[0] == pressedLink) {
                                    if (delegate != null) {
                                        String url = link[0].getURL();
                                        if (url.startsWith("game")) {
                                            delegate.didPressedReplyMessage(this, currentMessageObject.messageOwner.reply_to_msg_id);
                                            /*TLRPC.KeyboardButton gameButton = null;
                                            MessageObject messageObject = currentMessageObject.replyMessageObject;
                                            if (messageObject != null && messageObject.messageOwner.reply_markup != null) {
                                                for (int a = 0; a < messageObject.messageOwner.reply_markup.rows.size(); a++) {
                                                    TLRPC.TL_keyboardButtonRow row = messageObject.messageOwner.reply_markup.rows.get(a);
                                                    for (int b = 0; b < row.buttons.size(); b++) {
                                                        TLRPC.KeyboardButton button = row.buttons.get(b);
                                                        if (button instanceof TLRPC.TL_keyboardButtonGame && button.game_id == currentMessageObject.messageOwner.action.game_id) {
                                                            gameButton = button;
                                                            break;
                                                        }
                                                    }
                                                    if (gameButton != null) {
                                                        break;
                                                    }
                                                }
                                            }
                                            if (gameButton != null) {
                                                delegate.didPressedBotButton(messageObject, gameButton);
                                            }*/
                                        } else {
                                            delegate.needOpenUserProfile(Integer.parseInt(url));
                                        }
                                    }
                                    result = true;
                                }
                            }
                        } else {
                            pressedLink = null;
                        }
                    } else {
                        pressedLink = null;
                    }
                } else {
                    pressedLink = null;
                }
            }
        }

        if (!result) {
            result = super.onTouchEvent(event);
        }

        return result;
    }

    private void createLayout(CharSequence text, int width) {
        int maxWidth = width - AndroidUtilities.dp(30);
        textLayout = new StaticLayout(text, Theme.chat_actionTextPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        textHeight = 0;
        textWidth = 0;
        try {
            int linesCount = textLayout.getLineCount();
            for (int a = 0; a < linesCount; a++) {
                float lineWidth;
                try {
                    lineWidth = textLayout.getLineWidth(a);
                    if (lineWidth > maxWidth) {
                        lineWidth = maxWidth;
                    }
                    textHeight = (int)Math.max(textHeight, Math.ceil(textLayout.getLineBottom(a)));
                } catch (Exception e) {
                    FileLog.e(e);
                    return;
                }
                textWidth = (int)Math.max(textWidth, Math.ceil(lineWidth));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        textX = (width - textWidth) / 2;
        textY = AndroidUtilities.dp(7);
        textXLeft = (width - textLayout.getWidth()) / 2;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (currentMessageObject == null && customText == null) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), textHeight + AndroidUtilities.dp(14));
            return;
        }
        int width = Math.max(AndroidUtilities.dp(30), MeasureSpec.getSize(widthMeasureSpec));
        if (width != previousWidth) {
            CharSequence text;
            if (currentMessageObject != null) {
                text = currentMessageObject.messageText;
            } else {
                text = customText;
            }
            previousWidth = width;
            createLayout(text, width);
            if (currentMessageObject != null && currentMessageObject.type == 11) {
                imageReceiver.setImageCoords((width - AndroidUtilities.dp(64)) / 2, textHeight + AndroidUtilities.dp(15), AndroidUtilities.dp(64), AndroidUtilities.dp(64));
            }
        }
        setMeasuredDimension(width, textHeight + AndroidUtilities.dp(14 + (currentMessageObject != null && currentMessageObject.type == 11 ? 70 : 0)));
    }

    public int getCustomDate() {
        return customDate;
    }

    private int findMaxWidthAroundLine(int line) {
        int width = (int) Math.ceil(textLayout.getLineWidth(line));
        int count = textLayout.getLineCount();
        for (int a = line + 1; a < count; a++) {
            int w = (int) Math.ceil(textLayout.getLineWidth(a));
            if (Math.abs(w - width) < AndroidUtilities.dp(12)) {
                width = Math.max(w, width);
            } else {
                break;
            }
        }
        for (int a = line - 1; a >= 0; a--) {
            int w = (int) Math.ceil(textLayout.getLineWidth(a));
            if (Math.abs(w - width) < AndroidUtilities.dp(12)) {
                width = Math.max(w, width);
            } else {
                break;
            }
        }
        return width;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentMessageObject != null && currentMessageObject.type == 11) {
            imageReceiver.draw(canvas);
        }

        if (textLayout != null) {
            final int count = textLayout.getLineCount();
            final int corner = AndroidUtilities.dp(6);
            int y = AndroidUtilities.dp(7);
            int previousLineBottom = 0;
            int dx;
            int dy;
            for (int a = 0; a < count; a++) {
                int width = findMaxWidthAroundLine(a);
                int x = (getMeasuredWidth() - width) / 2 - AndroidUtilities.dp(3);
                width += AndroidUtilities.dp(6);
                int lineBottom = textLayout.getLineBottom(a);
                int height = lineBottom - previousLineBottom;
                int additionalHeight = 0;
                previousLineBottom = lineBottom;

                boolean drawBottomCorners = a == count - 1;
                boolean drawTopCorners = a == 0;

                if (drawTopCorners) {
                    y -= AndroidUtilities.dp(3);
                    height += AndroidUtilities.dp(3);
                }
                if (drawBottomCorners) {
                    height += AndroidUtilities.dp(3);
                }

                canvas.drawRect(x, y, x + width, y + height, Theme.chat_actionBackgroundPaint);

                if (!drawBottomCorners && a + 1 < count) {
                    int nextLineWidth = findMaxWidthAroundLine(a + 1) + AndroidUtilities.dp(6);
                    if (nextLineWidth + corner * 2 < width) {
                        int nextX = (getMeasuredWidth() - nextLineWidth) / 2;
                        drawBottomCorners = true;
                        additionalHeight = AndroidUtilities.dp(3);

                        canvas.drawRect(x, y + height, nextX, y + height + AndroidUtilities.dp(3), Theme.chat_actionBackgroundPaint);
                        canvas.drawRect(nextX + nextLineWidth, y + height, x + width, y + height + AndroidUtilities.dp(3), Theme.chat_actionBackgroundPaint);
                    } else if (width + corner * 2 < nextLineWidth) {
                        additionalHeight = AndroidUtilities.dp(3);

                        dy = y + height - AndroidUtilities.dp(9);

                        dx = x - corner * 2;
                        Theme.chat_cornerInner[2].setBounds(dx, dy, dx + corner, dy + corner);
                        Theme.chat_cornerInner[2].draw(canvas);

                        dx = x + width + corner;
                        Theme.chat_cornerInner[3].setBounds(dx, dy, dx + corner, dy + corner);
                        Theme.chat_cornerInner[3].draw(canvas);
                    } else {
                        additionalHeight = AndroidUtilities.dp(6);
                    }
                }
                if (!drawTopCorners && a > 0) {
                    int prevLineWidth = findMaxWidthAroundLine(a - 1) + AndroidUtilities.dp(6);
                    if (prevLineWidth + corner * 2 < width) {
                        int prevX = (getMeasuredWidth() - prevLineWidth) / 2;
                        drawTopCorners = true;
                        y -= AndroidUtilities.dp(3);
                        height += AndroidUtilities.dp(3);

                        canvas.drawRect(x, y, prevX, y + AndroidUtilities.dp(3), Theme.chat_actionBackgroundPaint);
                        canvas.drawRect(prevX + prevLineWidth, y, x + width, y + AndroidUtilities.dp(3), Theme.chat_actionBackgroundPaint);
                    } else if (width + corner * 2 < prevLineWidth) {
                        y -= AndroidUtilities.dp(3);
                        height += AndroidUtilities.dp(3);

                        dy = y + corner;

                        dx = x - corner * 2;
                        Theme.chat_cornerInner[0].setBounds(dx, dy, dx + corner, dy + corner);
                        Theme.chat_cornerInner[0].draw(canvas);

                        dx = x + width + corner;
                        Theme.chat_cornerInner[1].setBounds(dx, dy, dx + corner, dy + corner);
                        Theme.chat_cornerInner[1].draw(canvas);
                    } else {
                        y -= AndroidUtilities.dp(6);
                        height += AndroidUtilities.dp(6);
                    }
                }

                canvas.drawRect(x - corner, y + corner, x, y + height + additionalHeight - corner, Theme.chat_actionBackgroundPaint);
                canvas.drawRect(x + width, y + corner, x + width + corner, y + height + additionalHeight - corner, Theme.chat_actionBackgroundPaint);

                if (drawTopCorners) {
                    dx = x - corner;
                    Theme.chat_cornerOuter[0].setBounds(dx, y, dx + corner, y + corner);
                    Theme.chat_cornerOuter[0].draw(canvas);

                    dx = x + width;
                    Theme.chat_cornerOuter[1].setBounds(dx, y, dx + corner, y + corner);
                    Theme.chat_cornerOuter[1].draw(canvas);
                }

                if (drawBottomCorners) {
                    dy = y + height + additionalHeight - corner;

                    dx = x + width;
                    Theme.chat_cornerOuter[2].setBounds(dx, dy, dx + corner, dy + corner);
                    Theme.chat_cornerOuter[2].draw(canvas);

                    dx = x - corner;
                    Theme.chat_cornerOuter[3].setBounds(dx, dy, dx + corner, dy + corner);
                    Theme.chat_cornerOuter[3].draw(canvas);
                }

                y += height;
            }

            canvas.save();
            canvas.translate(textXLeft, textY);
            textLayout.draw(canvas);
            canvas.restore();
        }
    }
}
