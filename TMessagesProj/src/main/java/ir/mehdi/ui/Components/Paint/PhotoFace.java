package ir.mehdi.ui.Components.Paint;

import android.graphics.Bitmap;
import android.graphics.PointF;

import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.Landmark;

import ir.mehdi.ui.Components.Size;

import java.util.List;

public class PhotoFace {

    private float width;
    private float angle;

    private ir.mehdi.ui.Components.Point foreheadPoint;

    private ir.mehdi.ui.Components.Point eyesCenterPoint;
    private float                           eyesDistance;

    private ir.mehdi.ui.Components.Point mouthPoint;
    private ir.mehdi.ui.Components.Point chinPoint;

    public PhotoFace(Face face, Bitmap sourceBitmap, Size targetSize, boolean sideward) {
        List<Landmark> landmarks = face.getLandmarks();

        ir.mehdi.ui.Components.Point leftEyePoint  = null;
        ir.mehdi.ui.Components.Point rightEyePoint = null;

        ir.mehdi.ui.Components.Point leftMouthPoint  = null;
        ir.mehdi.ui.Components.Point rightMouthPoint = null;

        for (Landmark landmark : landmarks) {
            PointF point = landmark.getPosition();

            switch (landmark.getType()) {
                case Landmark.LEFT_EYE: {
                    leftEyePoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;

                case Landmark.RIGHT_EYE: {
                    rightEyePoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;

                case Landmark.LEFT_MOUTH: {
                    leftMouthPoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;

                case Landmark.RIGHT_MOUTH: {
                    rightMouthPoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;
            }
        }

        if (leftEyePoint != null && rightEyePoint != null) {
            eyesCenterPoint = new ir.mehdi.ui.Components.Point(0.5f * leftEyePoint.x + 0.5f * rightEyePoint.x,
                                                                  0.5f * leftEyePoint.y + 0.5f * rightEyePoint.y);
            eyesDistance = (float)Math.hypot(rightEyePoint.x - leftEyePoint.x, rightEyePoint.y - leftEyePoint.y);
            angle = (float)Math.toDegrees(Math.PI + Math.atan2(rightEyePoint.y - leftEyePoint.y, rightEyePoint.x - leftEyePoint.x));

            width = eyesDistance * 2.35f;

            float foreheadHeight = 0.8f * eyesDistance;
            float upAngle = (float)Math.toRadians(angle - 90);
            foreheadPoint = new ir.mehdi.ui.Components.Point(eyesCenterPoint.x + foreheadHeight * (float)Math.cos(upAngle),
                                                                eyesCenterPoint.y + foreheadHeight * (float)Math.sin(upAngle));
        }

        if (leftMouthPoint != null && rightMouthPoint != null) {
            mouthPoint = new ir.mehdi.ui.Components.Point(0.5f * leftMouthPoint.x + 0.5f * rightMouthPoint.x,
                                                             0.5f * leftMouthPoint.y + 0.5f * rightMouthPoint.y);

            float chinDepth = 0.7f * eyesDistance;
            float downAngle = (float)Math.toRadians(angle + 90);
            chinPoint = new ir.mehdi.ui.Components.Point(mouthPoint.x + chinDepth * (float)Math.cos(downAngle),
                                                            mouthPoint.y + chinDepth * (float)Math.sin(downAngle));
        }
    }

    public boolean isSufficient() {
        return eyesCenterPoint != null;
    }

    private ir.mehdi.ui.Components.Point transposePoint(PointF point, Bitmap sourceBitmap, Size targetSize, boolean sideward) {
        float bitmapW = sideward ? sourceBitmap.getHeight() : sourceBitmap.getWidth();
        float bitmapH = sideward ? sourceBitmap.getWidth() : sourceBitmap.getHeight();
        return new ir.mehdi.ui.Components.Point(targetSize.width * point.x / bitmapW,
                                                   targetSize.height * point.y / bitmapH);
    }

    public ir.mehdi.ui.Components.Point getPointForAnchor(int anchor) {
        switch (anchor) {
            case 0: {
                return foreheadPoint;
            }

            case 1: {
                return eyesCenterPoint;
            }

            case 2: {
                return mouthPoint;
            }

            case 3: {
                return chinPoint;
            }

            default: {
                return null;
            }
        }
    }

    public float getWidthForAnchor(int anchor) {
        if (anchor == 1)
            return eyesDistance;

        return width;
    }

    public float getAngle() {
        return angle;
    }

 }
