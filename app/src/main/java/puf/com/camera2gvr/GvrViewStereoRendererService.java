package puf.com.camera2gvr;

import android.graphics.SurfaceTexture;
import android.opengl.GLES10;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES32;
import android.opengl.Matrix;
import android.os.HandlerThread;
import android.util.Log;

import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * This class use to render the texture from Camera to GvrView with library OpenGL
 * We can implements GvrView.StereoRenderer on MainActivity but my team want to divise to separate part
 * And in this part it have only one responsability about renderer. (Single Responsability)
 */
public class GvrViewStereoRendererService implements GvrView.StereoRenderer {

	private static final String TAG = "GvrViewStereoRendererService";

	private static final String VERTEX_SHADER =
			"attribute vec4 position;\n" +
			"attribute vec4 in_tex\n;" +
			"varying vec2 out_tex\n;" +
			"uniform mat4 mvpMatrix;\n" +
			"uniform mat4 texMatrix;\n" +
			"void main() {\n" +
			"	gl_Position = mvpMatrix * position;\n" +
			"	out_tex = (texMatrix * in_tex).xy;\n" +
			"}\n";

	private static String FRAGMENT_SHADER="";

	// ======================== Normal ================================
	private static final String FRAGMENT_SHADER_NORMAL =
			"#extension GL_OES_EGL_image_external : require\n" +
			"precision mediump float;\n" +
			"varying vec2 out_tex;\n" +
			"\n" +
			"uniform samplerExternalOES oes_tex;\n" +
			"\n" +
			"void main() {\n" +
				"	gl_FragColor = texture2D(oes_tex, out_tex);\n" +
			"}\n";

	// ====================== Deuteranomaly ====================================
	private static final String FRAGMENT_SHADER_DETERRANOMALY =
			"#extension GL_OES_EGL_image_external : require\n" +
					"precision mediump float;\n" +
					"varying vec2 out_tex;\n" +
					"\n" +
					"uniform samplerExternalOES oes_tex;\n" +
					"\n" +
					"void main() {\n" +
					"  vec4 color = texture2D(oes_tex, out_tex);\n" +
					"  float colorR = ( 0.5 + color.r ) /1.0;\n" +
					"  float colorG = (color.g -0.2) /1.0;\n" +
					"  float colorB = (  color.b + 0.25) /1.0;\n" +
					"  gl_FragColor = vec4(colorR, colorG, colorB, color.a);\n" +
					"}\n";

	// ====================== Protanomaly ====================================
	private static final String FRAGMENT_SHADER_PROTANOMALY =
			"#extension GL_OES_EGL_image_external : require\n" +
					"precision mediump float;\n" +
					"varying vec2 out_tex;\n" +
					"\n" +
					"uniform samplerExternalOES oes_tex;\n" +
					"\n" +
					"void main() {\n" +
					"  vec4 color = texture2D(oes_tex, out_tex);\n" +
					"  float colorR = ( color.r - 0.2) /1.0;\n" +
					"  float colorG = (color.g + 0.5) /1.0;\n" +
					"  float colorB = (  color.b + 0.25) /1.0;\n" +
					"  gl_FragColor = vec4(colorR, colorG, colorB, color.a);\n" +
					"}\n";




	// ====================== Black And White ====================================
	/*private static final String FRAGMENT_SHADER =
			"#extension GL_OES_EGL_image_external : require\n" +
					"precision mediump float;\n" +
					"varying vec2 out_tex;\n" +
					"\n" +
					"uniform samplerExternalOES oes_tex;\n" +
					"\n" +
					"void main() {\n" +
					"	vec4 color = texture2D(oes_tex, out_tex);\n" +
					"	float colorR = (color.r + color.g + color.b) /3.0;\n" +
					"	float colorG = (color.r + color.g + color.b) /3.0;\n" +
					"	float colorB = (color.r + color.g + color.b) /3.0;\n" +
					"	gl_FragColor = vec4(colorR, colorG, colorB, color.a);\n" +
					"}\n";*/


	private GvrView surface;
	private OpenGLShader shader = null;
	private SurfaceTexture surfaceTexture = null;
	private HandlerThread drawThread = null;

	private int textureId = 0;

	private static final float Z_NEAR = 0.1f;
	private static final float Z_FAR = 1000.0f;

	// Position the eye in front of the origin.
	final float eyeX = 0.0f;
	final float eyeY = 0.0f;
	final float eyeZ = 0.0f;

	// We are looking toward the distance
	final float lookX = 0.0f;
	final float lookY = 0.0f;
	final float lookZ = -100.0f;

	// Set our up vector. This is where our head would be pointing were we holding the camera.
	final float upX = 0.0f;
	final float upY = 1.0f;
	final float upZ = 0.0f;

	private float[] cameraViewMatrix;
	private float[] viewMatrix;

	private float[] model;
	private float[] modelView;
	private float[] modelViewProjection;

	private float[] transformMatrix;

	/*This FloatBuffer RECT_VERTICES render 2 the size of rectangle in GvrView
	* If you want change the size of 2 rectangle (side by side you can fix them)
	* The FloatBuffer below already tested on device 5,5 inch (android 7)
	*/
	private static final FloatBuffer RECT_VERTICES = RenderUtils.createFloatBuffer(new float[] {
			-2.0f, -2.0f,
			2.0f, -2.0f,
			-2.0f, 2.0f,
			2.0f, 2.0f,
	});

	/*The FloatBuffer RECT_TEX_COORDS render the content of FloatBuffer RECT_VERTICES
	* The Coordination below is best coords to render the content in RECT_VERTICES
	* If you change another coords the texture is out side the rectangle
	*/
	private static final FloatBuffer RECT_TEX_COORDS = RenderUtils.createFloatBuffer(new float[] {
			0.0f, 0.0f,
			1.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 1.0f
	});

	private static final float[] verticalFlipMatrix =
			new float[] {
					1, 0, 0, 0,
					0, -1, 0, 0,
					0, 0, 1, 0,
					0, 1, 0, 1
			};

	/**
	 * We want the implementer @Override the methode onSurfaceTextureCreated
	 */
	public static interface GvrRendererEvents {
		public void onSurfaceTextureCreated(SurfaceTexture surfaceTexture);
	}

	GvrRendererEvents events;
    private int blinding = 0;

	public GvrViewStereoRendererService(GvrView surface, GvrRendererEvents event, int blinding ) {
		this.surface = surface;
		this.surface.setRenderer(this);
        this.blinding = blinding;
		cameraViewMatrix = new float[16];
		viewMatrix = new float[16];

		model = new float[16];
		modelView = new float[16];
		modelViewProjection = new float[16];
		transformMatrix = new float[16];
		events = event;

		if(this.blinding == 0)
			FRAGMENT_SHADER = FRAGMENT_SHADER_NORMAL;
		else  if(this.blinding == 1)
			FRAGMENT_SHADER = FRAGMENT_SHADER_DETERRANOMALY;
		else if(this.blinding == 2)
			FRAGMENT_SHADER = FRAGMENT_SHADER_PROTANOMALY;
	}

	public int setBlinding(int bliding){
        return this.blinding = bliding;
    }


	public SurfaceTexture getSurfaceTexture() {
		return surfaceTexture;
	}

	@Override
	public void onNewFrame(HeadTransform headTransform) {
		surfaceTexture.updateTexImage();
		surfaceTexture.getTransformMatrix(transformMatrix);
		Matrix.rotateM(transformMatrix, 0, 270, 0, 0, 1);
		Matrix.translateM(transformMatrix, 0, -1, 0, 0);
	}

	@Override
	public void onDrawEye(Eye eye) {
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		Matrix.multiplyMM(viewMatrix, 0, eye.getEyeView(), 0, cameraViewMatrix, 0);

		float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);

		Matrix.multiplyMM(modelView, 0, viewMatrix, 0, model, 0);
		Matrix.setIdentityM(modelView, 0);
		Matrix.translateM(modelView, 0, 0, 0, -2.0f);
		Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);


		shader.useProgram();
		GLES20.glUniformMatrix4fv(shader.getUniformLocation("mvpMatrix"), 1, false, modelViewProjection, 0);
		GLES20.glUniform1i(shader.getUniformLocation("oes_tex"), 0);
		RenderUtils.checkNoGLES2Error("Initialize fragment shader uniform values.");

		shader.setVertexAttribArray("position", 2, RECT_VERTICES);
		shader.setVertexAttribArray("in_tex", 2, RECT_TEX_COORDS);


		// Texture transformation
		GLES20.glUniformMatrix4fv(shader.getUniformLocation("texMatrix"), 1, false, transformMatrix, 0);


		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);


	}

	@Override
	public void onFinishFrame(Viewport viewport) {

	}

	@Override
	public void onSurfaceChanged(int width, int height) {
		Log.d(TAG, "GvrViewStereoRendererService.onSurfaceChanged");
		surfaceTexture.setDefaultBufferSize(width, height);
	}

	@Override
	public void onSurfaceCreated(EGLConfig eglConfig) {
		Log.d(TAG, "GvrViewStereoRendererService.onSurfaceCreated");

		// Create texture
		textureId = RenderUtils.createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);


		surfaceTexture = new SurfaceTexture(textureId);

		events.onSurfaceTextureCreated(surfaceTexture);

		// Create Shader
		shader = new OpenGLShader(VERTEX_SHADER, FRAGMENT_SHADER);
		shader.useProgram();


		Matrix.setLookAtM(cameraViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);
		Matrix.setIdentityM(model, 0);
		Matrix.translateM(model, 0, 0, 0, -2.0f);
	}

	@Override
	public void onRendererShutdown() {
		Log.d(TAG, "GvrViewStereoRendererService.onRendererShutdown");
		shader.release();
	}
}
