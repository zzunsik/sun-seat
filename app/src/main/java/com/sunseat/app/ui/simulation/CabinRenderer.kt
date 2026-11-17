package com.sunseat.app.ui.simulation

import android.content.Context
import android.view.Surface
import android.view.SurfaceView
import android.view.Choreographer
import com.google.android.filament.*
import com.google.android.filament.android.UiHelper
import com.sunseat.app.domain.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** UI-thread confined Filament resources. Geometry calculations run outside this owner. */
internal class CabinRenderer(context: Context, val surface: SurfaceView) : UiHelper.RendererCallback {
    companion object { init { Filament.init() } }
    private val engine=Engine.create()
    private val scene=engine.createScene()
    private val view=engine.createView()
    private val renderer=engine.createRenderer()
    private val cameraEntity=EntityManager.get().create()
    private val camera=engine.createCamera(cameraEntity)
    private val helper=UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swap: SwapChain?=null
    private val resources=mutableListOf<Part>()
    private val materials=mutableListOf<Material>()
    private val lightEntity=EntityManager.get().create()
    private var width=1; private var height=1
    private var closed=false; private var running=false; private var dirty=true
    private var previousFrame=0L
    private var profile: CabinProfile?=null
    private var light: CabinLight?=null
    private var selected: String?=null
    private var interior=false
    private var colors: List<FloatArray> = emptyList()
    private var body: Part?=null
    private var overlay: Part?=null
    private var beams: Part?=null
    private val lit: Material
    private val unlit: Material
    private val beamMaterial: Material
    private val ambient: IndirectLight
    private data class Part(val entity: Int,val vertices: VertexBuffer,val indices: IndexBuffer,val capacity: Int)
    init {
        try {
        fun material(name: String): Material {
            val bytes=context.assets.open("simulation/$name.filamat").use { it.readBytes() }
            val buffer=ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
            return Material.Builder().payload(buffer,buffer.remaining()).build(engine).also { materials+=it }
        }
        lit=material("cabin_lit"); unlit=material("cabin_unlit"); beamMaterial=material("cabin_beam")
        view.scene=scene; view.camera=camera
        ambient=IndirectLight.Builder().irradiance(1,floatArrayOf(.8f,.85f,.9f)).intensity(22000f).build(engine)
        scene.indirectLight=ambient
        view.isPostProcessingEnabled=false
        renderer.clearOptions=Renderer.ClearOptions().apply { clear=true; clearColor=doubleArrayOf(.94,.95,.97,1.0) }
        camera.setExposure(16f,1f/125f,100f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(1f,1f,1f).intensity(35000f)
            .direction(0f,-1f,0f).castShadows(true).build(engine,lightEntity)
        scene.addEntity(lightEntity)
        helper.renderCallback=this
        helper.attachTo(surface)
        } catch(error: Throwable) {
            helper.detach()
            engine.destroy()
            EntityManager.get().destroy(cameraEntity)
            EntityManager.get().destroy(lightEntity)
            throw error
        }
    }
    fun update(p: CabinProfile,l: CabinLight,selection: String?,inside: Boolean,palette: List<FloatArray>) {
        if(closed) return
        val paletteChanged=colors.size!=palette.size || colors.zip(palette).any { !it.first.contentEquals(it.second) }
        if(profile==p && light===l && selected==selection && interior==inside && !paletteChanged) return
        if(profile!=p || paletteChanged || interior!=inside) {
            body?.let(::destroyPart)
            body=create(CabinMesh.body(p,palette[0],palette[1],cutaway=!inside),lit)
        }
        profile=p; light=l; selected=selection; interior=inside; colors=palette
        val background=palette[5]
        renderer.clearOptions=Renderer.ClearOptions().apply { clear=true; clearColor=DoubleArray(4) { background[it].toDouble() } }
        val mesh=CabinMesh.overlays(p,l,selection,palette[2],palette[3],palette[4],palette[6])
        val previous=overlay
        if(previous==null || previous.capacity<mesh.count) {
            previous?.let(::destroyPart)
            overlay=create(mesh,unlit,shadow=false)
        } else {
            previous.vertices.setBufferAt(engine,0,mesh.buffer())
            engine.renderableManager.setGeometryAt(engine.renderableManager.getInstance(previous.entity),0,
                RenderableManager.PrimitiveType.TRIANGLES,previous.vertices,previous.indices,0,mesh.count)
        }
        val d=l.towardSun
        val beamMesh=CabinMesh.beams(p,l,selection,palette[2])
        val previousBeam=beams
        if(beamMesh.count==0) {
            previousBeam?.let(::destroyPart); beams=null
        } else if(previousBeam==null || previousBeam.capacity<beamMesh.count) {
            previousBeam?.let(::destroyPart); beams=create(beamMesh,beamMaterial,shadow=false)
        } else {
            previousBeam.vertices.setBufferAt(engine,0,beamMesh.buffer())
            engine.renderableManager.setGeometryAt(engine.renderableManager.getInstance(previousBeam.entity),0,
                RenderableManager.PrimitiveType.TRIANGLES,previousBeam.vertices,previousBeam.indices,0,beamMesh.count)
        }
        engine.lightManager.setDirection(engine.lightManager.getInstance(lightEntity),-d.x.toFloat(),-d.y.toFloat(),-d.z.toFloat())
        engine.lightManager.setIntensity(engine.lightManager.getInstance(lightEntity),if(l.available && d.y>0) 35000f else 0f)
        setCamera(); dirty=true
    }
    private fun create(mesh: CabinMesh,material: Material,shadow: Boolean=true): Part {
        val capacity=mesh.count+128
        val vertex=VertexBuffer.Builder().vertexCount(capacity).bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION,0,VertexBuffer.AttributeType.FLOAT3,0,44)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS,0,VertexBuffer.AttributeType.FLOAT4,12,44)
            .attribute(VertexBuffer.VertexAttribute.COLOR,0,VertexBuffer.AttributeType.FLOAT4,28,44).build(engine)
        vertex.setBufferAt(engine,0,mesh.buffer())
        val data=ByteBuffer.allocateDirect(capacity*4).order(ByteOrder.nativeOrder()).apply { repeat(capacity) { putInt(it) }; flip() }
        val index=IndexBuffer.Builder().indexCount(capacity).bufferType(IndexBuffer.Builder.IndexType.UINT).build(engine)
        index.setBuffer(engine,data)
        val entity=EntityManager.get().create()
        RenderableManager.Builder(1).boundingBox(Box(0f,2f,0f,8f,8f,8f))
            .geometry(0,RenderableManager.PrimitiveType.TRIANGLES,vertex,index,0,mesh.count).material(0,material.defaultInstance)
            .culling(false).castShadows(shadow).receiveShadows(shadow).build(engine,entity)
        scene.addEntity(entity)
        return Part(entity,vertex,index,capacity).also { resources+=it }
    }
    private fun destroyPart(p: Part) {
        scene.remove(p.entity); engine.destroyEntity(p.entity); EntityManager.get().destroy(p.entity)
        engine.destroyVertexBuffer(p.vertices); engine.destroyIndexBuffer(p.indices); resources.remove(p)
    }
    private fun setCamera() {
        val p=profile ?: return
        val seat=p.seats.firstOrNull { it.id==selected } ?: p.seats.first()
        if(interior) {
            val raw=seat.center+seat.facing*.9+seat.right*.55+V3(0.0,.75,0.0)
            val eye=V3(raw.x.coerceIn(-p.width/2+.08,p.width/2-.08),raw.y,raw.z.coerceIn(-p.length/2+.1,p.length/2-.1))
            val target=seat.center+V3(0.0,.18,0.0)
            camera.lookAt(eye.x,eye.y,eye.z,target.x,target.y,target.z,0.0,1.0,0.0)
            camera.setProjection(52.0,width.toDouble()/height,.03,40.0,Camera.Fov.VERTICAL)
        } else {
            val size=p.length+if(p.kind in listOf(CabinKind.CAR,CabinKind.TAXI)) .65 else 0.0
            camera.lookAt(size*.7,size*1.05,-size*.95,0.0,.55,0.0,0.0,1.0,0.0)
            camera.setProjection(42.0,width.toDouble()/height,.05,80.0,Camera.Fov.VERTICAL)
        }
    }
    private val frame=object: Choreographer.FrameCallback {
        override fun doFrame(time: Long) {
            if(!running || closed) return
            if(dirty && time-previousFrame>=33_000_000L && helper.isReadyToRender) swap?.let {
                if(renderer.beginFrame(it,time)) { renderer.render(view); renderer.endFrame(); dirty=false }
                previousFrame=time
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    fun active(value: Boolean) {
        if(closed || running==value) return
        running=value
        if(value) { dirty=true; Choreographer.getInstance().postFrameCallback(frame) }
        else Choreographer.getInstance().removeFrameCallback(frame)
    }
    override fun onNativeWindowChanged(surface: Surface) {
        swap?.let { engine.destroySwapChain(it) }; swap=engine.createSwapChain(surface); dirty=true
    }
    override fun onDetachedFromSurface() {
        swap?.let { engine.destroySwapChain(it); engine.flushAndWait() }; swap=null
    }
    override fun onResized(w: Int,h: Int) { width=w.coerceAtLeast(1); height=h.coerceAtLeast(1); view.viewport=Viewport(0,0,width,height); setCamera(); dirty=true }
    fun close() {
        if(closed) return
        active(false); helper.detach(); resources.toList().forEach(::destroyPart)
        engine.destroyEntity(lightEntity); EntityManager.get().destroy(lightEntity)
        engine.destroyView(view); engine.destroyRenderer(renderer); engine.destroyScene(scene)
        engine.destroyIndirectLight(ambient)
        engine.destroyCameraComponent(cameraEntity); EntityManager.get().destroy(cameraEntity)
        materials.forEach { engine.destroyMaterial(it) }; engine.destroy(); closed=true
    }
}
