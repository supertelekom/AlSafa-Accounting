package com.alsafa.native8.stylekit
import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/** Wraps rows to intrinsic child height; measures the available parent width, not screen pixels. */
class SafaGrid(context:Context,private val columns:(Int)->Int,private val gapDp:Int=10):ViewGroup(context) {
 private val density=resources.displayMetrics.density
 private val gap=(gapDp*density).toInt()
 private var cols=1;private var rowHeights=emptyList<Int>();private var cell=0
 init {layoutDirection=View.LAYOUT_DIRECTION_RTL}
 override fun generateDefaultLayoutParams()=LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT)
 override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
  val width=MeasureSpec.getSize(widthMeasureSpec);val inner=(width-paddingLeft-paddingRight).coerceAtLeast(0)
  cols=columns((inner/density).toInt()).coerceAtLeast(1)
  cell=((inner-gap*(cols-1))/cols).coerceAtLeast(0)
  val heights=mutableListOf<Int>();var index=0
  for(i in 0 until childCount){val v=getChildAt(i);if(v.visibility==GONE)continue
   v.measure(MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
   val row=index/cols;if(row==heights.size)heights.add(0);heights[row]=max(heights[row],v.measuredHeight);index++
  }
  rowHeights=heights
  setMeasuredDimension(resolveSize(width,widthMeasureSpec),resolveSize(heights.sum()+gap*(heights.size-1).coerceAtLeast(0)+paddingTop+paddingBottom,heightMeasureSpec))
 }
 override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){var index=0;var top=paddingTop
  for(i in 0 until childCount){val v=getChildAt(i);if(v.visibility==GONE)continue
   val col=index%cols;val row=index/cols
   if(col==0&&row>0)top+=rowHeights[row-1]+gap
   val x=if(layoutDirection==LAYOUT_DIRECTION_RTL)width-paddingRight-cell-col*(cell+gap) else paddingLeft+col*(cell+gap)
   v.layout(x,top,x+cell,top+v.measuredHeight);index++
  }
 }
}
