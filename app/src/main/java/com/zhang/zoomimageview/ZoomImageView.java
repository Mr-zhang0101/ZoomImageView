package com.zhang.zoomimageview;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

/**
 * Project: CustomView
 * Author:  张佳林
 * Version:  1.0
 * Date:    2017/8/21
 * Modify:  //TODO
 * Description: //TODO
 * Copyright notice:
 */

public class ZoomImageView extends ImageView implements ScaleGestureDetector.OnScaleGestureListener,
        View.OnTouchListener, ViewTreeObserver.OnGlobalLayoutListener {

    // 检测两个手指在屏幕上做缩放的手势工具类
    private ScaleGestureDetector mScaleGestureDetector;

    private float scale;

    // 图片缩放工具操作类Matrix
    private Matrix mScaleMatrix;

    private float[] matrixValues;

    // 图片放大的最大值
    public static final float SCALE_MAX = 10.0f;

    //初始化时的缩放比例，如果图片宽或高大于屏幕，此值将小于0
    private float initScale = 1.0f;

    // 是否是初次加载
    private boolean isFirst = true;
    private RectF matrixRectF;

    //记录上次触摸点个数
    int lastPointerCount;

    private boolean isCanDrag;
    private float mLastX;
    private float mLastY;
    private boolean isCheckLeftAndRight;
    private boolean isCheckTopAndBottom;
    private double mTouchSlop;
    private GestureDetector mGestureDetector;
    boolean isAutoScale;
    //自动缩放中的节点
    private float SCALE_MID = 2f;

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mScaleMatrix = new Matrix();
        matrixValues = new float[9];
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
        mGestureDetector = new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
//            private boolean isAutoScale;

            /**
             * 原意：双击时，第二次touch事件的down发生时就会执行
             * @param e
             * @return  事件消费，返回true
             */
            @Override
            public boolean onDoubleTap(MotionEvent e) {

//                只缩放一次
//                if (isAutoScale == true){
//                    return true;
//                }
                float x = e.getX();
                float y = e.getY();
                //如果是缩放级别小于2，我们双击直接到变为原图的2倍
                if (getScale() < SCALE_MID){
                    ZoomImageView.this.postDelayed(new AutoScaleRunnable(SCALE_MID, x, y),16);
//                    isAutoScale = true;
                }else if (getScale() >= SCALE_MID && getScale() <SCALE_MAX){
                    ZoomImageView.this.postDelayed(
                            new AutoScaleRunnable(SCALE_MAX, x, y), 16);
//                    isAutoScale = true;
                }else{
                    //还原
                    ZoomImageView.this.postDelayed(
                            new AutoScaleRunnable(initScale, x, y), 16);
//                    isAutoScale = true;
                }
                return true;
            }
        });
        //获得的是触发移动事件的最短距离，如果小于这个距离就不触发移动控件
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        super.setScaleType(ScaleType.MATRIX);
        this.setOnTouchListener(this);
    }

    /**
     * 自动缩放的任务
     */
    private class AutoScaleRunnable implements Runnable{

        static final float BIGGER = 1.07f;
        static final float SMALLER = 0.93f;
        private float mTargetScale;
        private float tmpScale;
        /**
         * 缩放的中心
         */
        private float x;
        private float y;

        /**
         * 传入目标缩放值，根据目标值与当前值，判断应该放大还是缩小
         *
         * @param targetScale
         */
        public AutoScaleRunnable(float targetScale, float x, float y) {
            this.mTargetScale = targetScale;
            this.x = x;
            this.y = y;
            if (getScale() < mTargetScale){
                tmpScale = BIGGER;
            }else{
                tmpScale = SMALLER;
            }
        }

        @Override
        public void run() {
            //进行缩放
            mScaleMatrix.postScale(tmpScale,tmpScale,x,y);
            checkBorderAndCenterWhenScale();
            setImageMatrix(mScaleMatrix);

            float currentScale = getScale();
            //如果值在合法范围内，继续缩放
            if (((tmpScale > 1f) && (currentScale < mTargetScale))|| ((tmpScale <1f) && (mTargetScale < currentScale))){
                ZoomImageView.this.postDelayed(this,16);
            }else{//设置为目标的缩放比例
                float deltaScale = mTargetScale / currentScale;
                mScaleMatrix.postScale(deltaScale , deltaScale , x , y);
                checkBorderAndCenterWhenScale();
                setImageMatrix(mScaleMatrix);
                isAutoScale = false;
            }
        }
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float scale = getScale();
        // 前一个伸缩事件至当前伸缩事件的伸缩比率
        float scaleFactor = detector.getScaleFactor();
        if (getDrawable() == null) {
            return true;
        }
        //缩放的范围控制
        if ((scale < SCALE_MAX &&  scaleFactor > 1.0f)||(scale > initScale && scaleFactor <1.0f) ){

            //最大值最小值判断
            if (scaleFactor * scale <initScale){
                scaleFactor = initScale / scale;
            }
            if (scaleFactor * scale > SCALE_MAX){
                scaleFactor = SCALE_MAX / scale;
            }
            mScaleMatrix.postScale(scaleFactor,scaleFactor,detector.getFocusX(),detector.getFocusY());
            checkBorderAndCenterWhenScale();
            setImageMatrix(mScaleMatrix);
        }
        return true;
    }

    /**
     * 在缩放时，进行图片显示范围的控制
     */
    private void checkBorderAndCenterWhenScale() {
        RectF rect = getMatrixRectF();
        float deltaX = 0;
        float deltaY = 0;
        int width = getWidth();
        int height = getHeight();
        //如果宽或高大于屏幕，则控制范围，防止出现白边
        if (rect.width() >= width){
            if (rect.left >0){
                deltaX = -rect.left;
            }
            if (rect.right < width){
                deltaX = width - rect.right;
            }
        }
        if (rect.height() >= height){
            if (rect.top > 0){
                deltaY = -rect.top;
            }
            if (rect.bottom < height){
                deltaY = height - rect.bottom;
            }
        }
        // 如果宽度或者高度小于控件的宽或者高；则让其居中
        if (rect.width() < width){
            deltaX = width *0.5f -rect.right + 0.5f*rect.width();
        }
        if (rect.height() < height){
            deltaY = height * 0.5f -rect.bottom + 0.5f *rect.height();
        }
        mScaleMatrix.postTranslate(deltaX,deltaY);
    }

    /**
     * 根据当前图片的Matrix获取图片的范围
     * @return
     */
    public RectF getMatrixRectF() {
        Matrix matrix = mScaleMatrix;
        RectF rect = new RectF();
        Drawable d = getDrawable();
        if (d != null){
            rect.set(0,0,d.getIntrinsicWidth(),d.getIntrinsicHeight());
            matrix.mapRect(rect);
        }
        return rect;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        RectF rectF = getMatrixRectF();
        mScaleGestureDetector.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);
        float x = 0,y = 0;
        // 拿到触摸点的个数
        int pointerCount = event.getPointerCount();
        // 得到多个触摸点的x与y均值
        for (int i = 0; i < pointerCount; i++) {
            x += event.getX();
            y += event.getY();
        }
        x = x / pointerCount;
        y = y / pointerCount;
        //每当触摸点发生变化时，重置mLasX , mLastY
        if (pointerCount != lastPointerCount){
            isCanDrag = false;
            mLastX = x;
            mLastY = y;
        }
        lastPointerCount = pointerCount;
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                float dx = x - mLastX;
                float dy = y - mLastY;
                if (!isCanDrag){
                    isCanDrag = isCanDrag(dx,dy);
                }
                if (isCanDrag){
                    if (getDrawable() != null){
                        isCheckLeftAndRight = isCheckTopAndBottom = true;
                        // 如果宽度小于屏幕宽度，则禁止左右移动
                        if (rectF.width() < getWidth()){
                            dx = 0;
                            isCheckLeftAndRight = false;
                        }
                        // 如果高度小于屏幕高度，则禁止上下移动
                        if (rectF.height() < getHeight()){
                            dy = 0;
                            isCheckTopAndBottom = false;
                        }
                        if (rectF.left == 0 && dx > 0) {
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }

                        if (rectF.right == getWidth() && dx < 0) {
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        mScaleMatrix.postTranslate(dx,dy);
                        checkMatrixBounds();
                        setImageMatrix(mScaleMatrix);
                    }
                }
                mLastX = x;
                mLastY = y;
                if (rectF.width() > getWidth() || rectF.height() > getHeight()) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_DOWN:
                rectF =  getMatrixRectF();
                if (rectF.width() > getWidth() || rectF.height() > getHeight()) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastPointerCount = 0;
        }
        return true;
    }

    /**
     * 移动时，进行边界判断，主要判断宽或高大于屏幕的
     */
    private void checkMatrixBounds() {
        RectF rect = getMatrixRectF();
        float deltaX = 0,deltaY = 0;
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        // 判断移动或缩放后，图片显示是否超出屏幕边界
        if (rect.top >0 && isCheckTopAndBottom){
            deltaY = -rect.top;
        }
        if (rect.bottom < viewHeight && isCheckTopAndBottom){
            deltaY = viewHeight - rect.bottom;
        }
        if (rect.left > 0 && isCheckLeftAndRight){
            deltaX = -rect.left;
        }
        if (rect.right < viewWidth && isCheckLeftAndRight){
            deltaX = viewWidth - rect.right;
        }
        mScaleMatrix.postTranslate(deltaX,deltaY);
    }

    /**
     * 是否是推动行为
     * @param dx
     * @param dy
     * @return
     */
    private boolean isCanDrag(float dx, float dy) {
        return Math.sqrt((dx * dx) + (dy *dy)) >= mTouchSlop;
    }

    // 当View加载完成时可能通过OnGlobalLayoutListener监听，在布局加载完成后获得一个view的宽高。
    @Override
    public void onGlobalLayout() {
        if (isFirst){
            Drawable d = getDrawable();
            if (d == null){
                return;
            }
            // 获取控件的宽度和高度
            int width = getWidth();
            int height = getHeight();
            // 获取到ImageView对应图片的宽度和高度
            int dw = d.getIntrinsicWidth();// 图片固有宽度
            int dh = d.getIntrinsicHeight();
            float scale = 1.0f;
            // 图片宽度大于控件宽度 & 图片的高度小于控件高度
            if (dw > width && dh <= height){
                scale = width *1.0f / dw;
            }
            // 图片高度大于控件高度 & 图片的宽度小于控件的宽度
            if (dh > height && dw <= width){
                scale = height * 1.0f / dh;
            }
            // 图片宽度大于控件宽度 & 图片高度大于控件高度
            if (dw >width && dh >height){
                scale = Math.min(dw * 1.0f /width,dh*1.0f/height);
            }
            initScale = scale;
            // 将图片移动到手机屏幕的中间位置
            mScaleMatrix.postTranslate((width - dw) / 2,(height - dh) /2);
            mScaleMatrix.postScale(scale,scale,getWidth()/2,getHeight()/2);
            setImageMatrix(mScaleMatrix);
            isFirst = false;
        }
    }

    // 当view被附着到一个窗口时触发
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    // 当view离开附着的窗口时触发
    @SuppressWarnings("deprecation")
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeGlobalOnLayoutListener(this);
    }

    /**
     * 获取当前的缩放比例
     * @return
     */
    public float getScale() {
        mScaleMatrix.getValues(matrixValues);
        // 变化的倍数
        return matrixValues[Matrix.MSCALE_X];
    }
}
