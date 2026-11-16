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
    companion object {
        fun body(p: CabinProfile, neutral: FloatArray, seat: FloatArray): CabinMesh = CabinMesh().apply {
            val w=p.width/2; val l=p.length/2
            box(CabinBox(V3(-w,-.12,-l),V3(w,0.0,l)),neutral)
            for(x in listOf(-w,w)) {
                box(CabinBox(V3(x-.035,0.0,-l),V3(x+.035,p.sill,l)),neutral)
                box(CabinBox(V3(x-.035,p.windowTop,-l),V3(x+.035,p.height,l)),neutral)
            }
            for(z in listOf(-l,l)) {
                box(CabinBox(V3(-w,0.0,z-.035),V3(w,p.sill,z+.035)),neutral)
                box(CabinBox(V3(-w,p.windowTop,z-.035),V3(w,p.height,z+.035)),neutral)
                if(p.kind==CabinKind.SUBWAY) box(CabinBox(V3(-w,p.sill,z-.035),V3(w,p.windowTop,z+.035)),neutral)
            }
            p.pillars.forEach { box(it,neutral) }
            p.seats.forEach { s ->
                bevelBox(s.backBox,seat)
                bevelBox(CabinBox(s.center+V3(-s.width/2,-.12,-s.depth/2),s.center+V3(s.width/2,-.012,s.depth/2)),seat)
                box(CabinBox(s.center+V3(-.035,-s.center.y,-.035),s.center+V3(.035,-.12,.035)),neutral)
            }
            p.furniture.forEach { box(it,seat) }
            // Front dashboard and a neutral arrow establish the vehicle's forward direction.
            val arrow=V3(0.0,.015,-l+.65)
            ray(arrow,arrow+V3(0.0,0.0,-.35),seat)
            ray(arrow+V3(0.0,0.0,-.35),arrow+V3(.13,0.0,-.18),seat)
            ray(arrow+V3(0.0,0.0,-.35),arrow+V3(-.13,0.0,-.18),seat)
        }
        fun overlays(p: CabinProfile, light: CabinLight, selected: String?, sun: FloatArray, shade: FloatArray, ink: FloatArray): CabinMesh = CabinMesh().apply {
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
            }
            if(light.available && light.towardSun.y>0) {
                val origin=V3(0.0,p.height+.12,0.0)
                val end=origin+light.towardSun*1.15
                ray(origin,end,sun)
                sphere(end,.12,sun)
                light.cells.firstOrNull { it.seatId==selected && it.window!=null }?.let {
                    ray(it.center,it.center+light.towardSun*1.5,sun)
                    val cross=light.towardSun.cross(V3(0.0,1.0,.001)).unit()*.08
                    ray(it.center,it.center+light.towardSun*.18+cross,sun)
                    ray(it.center,it.center+light.towardSun*.18-cross,sun)
                }
            }
        }
    }
}
