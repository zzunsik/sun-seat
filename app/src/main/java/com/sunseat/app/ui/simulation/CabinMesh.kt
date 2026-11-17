package com.sunseat.app.ui.simulation

import com.sunseat.app.domain.*
import kotlin.math.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Shared deterministic geometry. Vertex = position(3), tangent quaternion(4), color(4). */
internal class CabinMesh {
    private var vertices = FloatArray(65_536)
    private var used=0
    val count get() = used / 11
    fun buffer(): ByteBuffer = ByteBuffer.allocateDirect(used*4).order(ByteOrder.nativeOrder()).apply {
        asFloatBuffer().put(vertices,0,used)
    }
    private fun vertex(p: V3, n: V3, color: FloatArray) {
        // Quaternion taking tangent-space +Z to the surface normal.
        val q = if(n.z < -.999) floatArrayOf(0f,1f,0f,0f) else {
            val w=sqrt((1+n.z)*2); floatArrayOf((-n.y/w).toFloat(),(n.x/w).toFloat(),0f,(w/2).toFloat())
        }
        if(used+11>vertices.size) vertices=vertices.copyOf(vertices.size*2)
        vertices[used++]=p.x.toFloat(); vertices[used++]=p.y.toFloat(); vertices[used++]=p.z.toFloat()
        for(v in q) vertices[used++]=v
        for(v in color) vertices[used++]=v
    }
    fun quad(a: V3, b: V3, c: V3, d: V3, color: FloatArray) {
        val n=(b-a).cross(c-a).unit()
        for(p in listOf(a,b,c,a,c,d)) vertex(p,n,color)
    }
    fun box(b: CabinBox, color: FloatArray) {
        val (x,y,z)=b.min; val (X,Y,Z)=b.max
        quad(V3(x,y,z),V3(x,y,Z),V3(x,Y,Z),V3(x,Y,z),color)
        quad(V3(X,y,Z),V3(X,y,z),V3(X,Y,z),V3(X,Y,Z),color)
        quad(V3(x,y,Z),V3(x,y,z),V3(X,y,z),V3(X,y,Z),color)
        quad(V3(x,Y,z),V3(x,Y,Z),V3(X,Y,Z),V3(X,Y,z),color)
        quad(V3(X,y,z),V3(x,y,z),V3(x,Y,z),V3(X,Y,z),color)
        quad(V3(x,y,Z),V3(X,y,Z),V3(X,Y,Z),V3(x,Y,Z),color)
    }
    fun ray(a: V3,b: V3,color: FloatArray,radius: Double=.009) {
        val axis=(b-a).unit(); val side=axis.cross(V3(0.0,1.0,.01)).unit()*radius
        val up=axis.cross(side).unit()*radius
        quad(a-side,b-side,b+side,a+side,color)
        quad(a-up,b-up,b+up,a+up,color)
    }
    fun sphere(center: V3,radius: Double,color: FloatArray) {
        for(row in 0 until 10) for(col in 0 until 16) {
            fun p(r: Int,c: Int): V3 {
                val h=PI*r/10; val a=2*PI*c/16
                return center+V3(sin(h)*cos(a),cos(h),sin(h)*sin(a))*radius
            }
            quad(p(row,col),p(row+1,col),p(row+1,col+1),p(row,col+1),color)
        }
    }
    fun bevelBox(b: CabinBox,color: FloatArray) {
        val r=.035
        val (x,y,z)=b.min; val (X,Y,Z)=b.max
        val outline=listOf(x+r to z,X-r to z,X to z+r,X to Z-r,X-r to Z,x+r to Z,x to Z-r,x to z+r)
        for(i in outline.indices) {
            val (a,c)=outline[i]; val (d,e)=outline[(i+1)%outline.size]
            quad(V3(a,y,c),V3(d,y,e),V3(d,Y-.02,e),V3(a,Y-.02,c),color)
            quad(V3(a,Y-.02,c),V3(d,Y-.02,e),V3(d.coerceIn(x+r,X-r),Y,e.coerceIn(z+r,Z-r)),V3(a.coerceIn(x+r,X-r),Y,c.coerceIn(z+r,Z-r)),color)
        }
        quad(V3(x+r,Y,z+r),V3(x+r,Y,Z-r),V3(X-r,Y,Z-r),V3(X-r,Y,z+r),color)
    }
    /** Rounded padded panel. Flat central contact surface stays aligned with CPU seat samples. */
    fun panel(center: V3,right: V3,up: V3,normal: V3,width: Double,height: Double,thickness: Double,color: FloatArray,radius: Double=.065) {
        val w=width/2; val h=height/2; val r=minOf(radius,w*.8,h*.8)
        val points=buildList {
            for(corner in 0..3) for(step in 0..5) {
                val angle=(corner*90+step*18)*PI/180
                val cx=if(corner==0 || corner==3) w-r else -w+r
                val cy=if(corner<2) h-r else -h+r
                add(cx+r*cos(angle) to cy+r*sin(angle))
            }
        }
        fun point(p: Pair<Double,Double>,depth: Double,scale: Double=1.0)=center+right*(p.first*scale)+up*(p.second*scale)+normal*depth
        for(i in points.indices) {
            val a=points[i]; val b=points[(i+1)%points.size]
            val frontA=point(a,thickness/2); val frontB=point(b,thickness/2)
            vertex(center+normal*(thickness/2),normal,color); vertex(frontA,normal,color); vertex(frontB,normal,color)
            quad(frontA,point(a,thickness/2-.025,1.04),point(b,thickness/2-.025,1.04),frontB,color)
            quad(point(a,thickness/2-.025,1.04),point(a,-thickness/2),point(b,-thickness/2),point(b,thickness/2-.025,1.04),color)
            vertex(center-normal*(thickness/2),normal*(-1.0),color); vertex(point(b,-thickness/2),normal*(-1.0),color); vertex(point(a,-thickness/2),normal*(-1.0),color)
        }
    }
    fun cylinder(a: V3,b: V3,radius: Double,color: FloatArray,segments: Int=24) {
        val axis=(b-a).unit(); val u=axis.cross(V3(.01,1.0,.001)).unit(); val v=axis.cross(u).unit()
        for(i in 0 until segments) {
            val n=u*cos(2*PI*i/segments)+v*sin(2*PI*i/segments)
            val m=u*cos(2*PI*(i+1)/segments)+v*sin(2*PI*(i+1)/segments)
            quad(a+n*radius,b+n*radius,b+m*radius,a+m*radius,color)
            vertex(a,axis*(-1.0),color);vertex(a+m*radius,axis*(-1.0),color);vertex(a+n*radius,axis*(-1.0),color)
            vertex(b,axis,color);vertex(b+n*radius,axis,color);vertex(b+m*radius,axis,color)
        }
    }
    private fun chair(s: CabinSeat,color: FloatArray,frame: FloatArray,headrest: Boolean) {
        panel(s.center-V3(0.0,.055,0.0),s.right,s.facing,V3(0.0,1.0,0.0),s.width+.05,s.depth+.05,.11,color)
        panel(s.center-s.facing*(s.depth/2+.045)+V3(0.0,.265,0.0),s.right,V3(0.0,1.0,0.0),s.facing,s.width+.05,.58,.09,color)
        cylinder(s.center-V3(0.0,s.center.y,0.0),s.center-V3(0.0,.1,0.0),.035,frame)
        if(headrest) {
            val head=s.center-s.facing*(s.depth/2)+V3(0.0,.66,0.0)
            for(x in listOf(-.085,.085)) cylinder(head+s.right*x-V3(0.0,.2,0.0),head+s.right*x,.012,frame)
            panel(head,s.right,V3(0.0,1.0,0.0),s.facing,.3,.2,.12,color,.06)
        }
    }
    companion object {
        fun body(p: CabinProfile, neutral: FloatArray, seat: FloatArray,cutaway: Boolean=true): CabinMesh = CabinMesh().apply {
            val w=p.width/2; val l=p.length/2
            val car=p.kind in listOf(CabinKind.CAR,CabinKind.TAXI)
            box(CabinBox(V3(-w,-.12,-l),V3(w,0.0,l)),neutral)
            for(x in listOf(-w,w)) {
                // Camera-facing wall is visually cut away; the closed calculation shell stays intact.
                bevelBox(CabinBox(V3(x-.045,0.0,-l),V3(x+.045,if(cutaway && x>0) .16 else p.sill,l)),neutral)
                if(x<0 || !cutaway) box(CabinBox(V3(x-.018,p.windowTop,-l),V3(x+.018,p.windowTop+.035,l)),neutral)
            }
            for(z in listOf(-l,l)) {
                box(CabinBox(V3(-w,0.0,z-.025),V3(w,if(cutaway && z<0) .16 else p.sill,z+.025)),neutral)
                if(z>0 || !cutaway) box(CabinBox(V3(-w,p.windowTop,z-.018),V3(w,p.windowTop+.035,z+.018)),neutral)
                if(p.kind==CabinKind.SUBWAY && (z>0 || !cutaway)) box(CabinBox(V3(-w,p.sill,z-.025),V3(w,p.windowTop,z+.025)),neutral)
            }
            p.pillars.filter { (it.min.x<0 && it.min.z> -l+.01) || !cutaway }.forEach { box(CabinBox(it.min,it.max.copy(y=p.windowTop+.035)),neutral) }
            p.seats.forEach { s ->
                chair(s,seat,neutral,car || p.kind==CabinKind.COACH)
            }
            p.furniture.take(1).forEach { bevelBox(it,seat) }
            if(car) {
                chair(CabinSeat("driver",SeatSpot(Side.LEFT),V3(-.43,.48,-.65),V3(0.0,0.0,-1.0)),seat,neutral,true)
                // Exterior below the window line identifies the sedan without obscuring seat contact surfaces.
                bevelBox(CabinBox(V3(-w-.06,.12,-l-.72),V3(w+.06,.65,-l)),neutral)
                bevelBox(CabinBox(V3(-w-.06,.12,l),V3(w+.06,.55,l+.4)),neutral)
                val rubber=floatArrayOf(.09f,.11f,.14f,1f)
                for(x in listOf(-w,w)) for(z in listOf(-l+.05,l-.25)) {
                    cylinder(V3(x-.1,.07,z),V3(x+.1,.07,z),.26,rubber)
                    val outer=x+if(x>0) .105 else -.105
                    cylinder(V3(outer-.008,.07,z),V3(outer+.008,.07,z),.14,neutral)
                }
                // Wheel is an open ring, not a solid disc across the windshield.
                val wheel=V3(-.43,.89,-l+.28)
                for(i in 0 until 24) {
                    val a=2*PI*i/24; val b=2*PI*(i+1)/24
                    cylinder(wheel+V3(cos(a)*.16,sin(a)*.14,0.0),wheel+V3(cos(b)*.16,sin(b)*.14,0.0),.018,seat,6)
                }
                for(s in listOf(-1.0,1.0)) ray(wheel,wheel+V3(s*.14,0.0,0.0),seat,.012)
            }
            // Front dashboard and a neutral arrow establish the vehicle's forward direction.
            val arrow=V3(0.0,.015,-l+.65)
            ray(arrow,arrow+V3(0.0,0.0,-.35),seat)
            ray(arrow+V3(0.0,0.0,-.35),arrow+V3(.13,0.0,-.18),seat)
            ray(arrow+V3(0.0,0.0,-.35),arrow+V3(-.13,0.0,-.18),seat)
        }
        fun overlays(p: CabinProfile, light: CabinLight, selected: String?, sun: FloatArray, shade: FloatArray, ink: FloatArray,onInk: FloatArray=shade): CabinMesh = CabinMesh().apply {
            val seats=p.seats.associateBy { it.id }
            light.cells.forEach { cell ->
                val s=seats.getValue(cell.seatId)
                val u=cell.column.toDouble()/CabinSimulation.GRID; val v=cell.row.toDouble()/CabinSimulation.GRID
                val du=1.0/CabinSimulation.GRID
                val normal=if(cell.surface==SeatSurface.CUSHION) V3(0.0,1.0,0.0) else s.facing
                val offset=normal*.008
                val points=listOf(s.sample(cell.surface,u,v),s.sample(cell.surface,u+du,v),s.sample(cell.surface,u+du,v+du),s.sample(cell.surface,u,v+du)).map { it+offset }
                // Explicit sunlight/shade overlays are the geometric verdict, independent of GPU brightness.
                val c=if(cell.window!=null) sun else shade
                quad(points[0],points[1],points[2],points[3],c)
            }
            p.seats.firstOrNull { it.id==selected }?.let { s ->
                val outline=listOf(s.sample(SeatSurface.CUSHION,0.0,0.0),s.sample(SeatSurface.CUSHION,1.0,0.0),s.sample(SeatSurface.CUSHION,1.0,1.0),s.sample(SeatSurface.CUSHION,0.0,1.0)).map { it+V3(0.0,.018,0.0) }
                for(i in 0..3) ray(outline[i],outline[(i+1)%4],ink,.025)
                val badge=s.center-s.facing*(s.depth/2)+V3(0.0,.93,0.0)
                val r=s.right*.11;val up=V3(0.0,.075,0.0)
                quad(badge-r-up,badge+r-up,badge+r+up,badge-r+up,ink)
                val front=s.facing*.004
                ray(badge+s.right*(-.055)+front,badge+s.right*(-.015)+V3(0.0,-.035,0.0)+front,onInk,.009)
                ray(badge+s.right*(-.015)+V3(0.0,-.035,0.0)+front,badge+s.right*.06+V3(0.0,.04,0.0)+front,onInk,.009)
                ray(badge-up,s.center-s.facing*(s.depth/2)+V3(0.0,.76,0.0),ink,.007)
            }
        }
        fun beams(p: CabinProfile,light: CabinLight,selected: String?,sun: FloatArray): CabinMesh = CabinMesh().apply {
            if(!light.available || light.towardSun.y<=0) return@apply
            val candidates=light.cells.filter { it.seatId==selected && it.windowPoint!=null }
            if(candidates.isEmpty()) return@apply
            val chosen=listOf(candidates.first(),candidates[candidates.size/2],candidates.last()).distinct()
            chosen.forEach { cell ->
                val seat=p.seats.first { it.id==cell.seatId }
                val n=if(cell.surface==SeatSurface.CUSHION) V3(0.0,1.0,0.0) else seat.facing
                val end=cell.center+n*.006
                val start=cell.windowPoint!!+light.towardSun*.55
                ray(start,end,sun.copyOf().apply { this[3]=.75f },.006)
                val tip=cell.windowPoint-light.towardSun*.12
                val side=light.towardSun.cross(V3(0.0,1.0,.001)).unit()*.045
                ray(tip,tip+light.towardSun*.12+side,sun,.006)
                ray(tip,tip+light.towardSun*.12-side,sun,.006)
            }
            // A weak parallel ribbon links two actual passing rays; never widen into a spotlight cone.
            if(chosen.size>1) {
                val a=chosen.first(); val b=chosen.last()
                val color=sun.copyOf().apply { this[3]=.1f }
                quad(a.windowPoint!!+light.towardSun*.55,b.windowPoint!!+light.towardSun*.55,b.center,a.center,color)
            }
        }
    }
}
