package triangle.black.TMail;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.util.Log;
import mindustry.gen.Tex;

/**
 * 全屏毛玻璃背景：截取当前场景到 FBO + 高斯模糊 shader 渲染，透明度随时间渐深（上限 0.85）。
 * 供收件箱等界面打开时作为背景使用；关闭时直接 remove()。
 *
 * 注意：arc 的 Shader 构造自带 preprocessor 处理 GL_ES 精度，源码里禁止写 #ifdef GL_ES，
 * 否则构造直接抛 ArcRuntimeException（"Shader contains GL_ES specific code"）。
 */
public class TriBlurBackground extends Element{

    /** 毛玻璃目标透明度（渐深上限）。 */
    private static final float MAX_ALPHA = 0.85f;
    /** 透明度渐深速度（每秒）。 */
    private static final float ALPHA_SPEED = 1.2f;

    private FrameBuffer fbo;
    private float alpha;
    private int fboWidth, fboHeight;
    private boolean logged;

    // 高斯模糊 shader（单 pass 水平高斯近似）
    private static Shader blurShader;
    private static boolean shaderFailed = false;
    private static final String BLUR_VERT =
        "attribute vec4 a_position;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "varying vec2 v_texCoord0;\n" +
        "void main(){\n" +
        "  v_texCoord0 = a_texCoord0;\n" +
        "  gl_Position = a_position;\n" +
        "}\n";
    private static final String BLUR_FRAG =
        "uniform sampler2D u_texture;\n" +
        "uniform float u_alpha;\n" +
        "uniform vec2 u_resolution;\n" +
        "varying vec2 v_texCoord0;\n" +
        "void main(){\n" +
        "  vec2 px = 1.0 / u_resolution;\n" +
        "  vec2 off1 = px * 1.3846153846;\n" +
        "  vec2 off2 = px * 3.2307692308;\n" +
        "  vec4 sum = texture2D(u_texture, v_texCoord0) * 0.2270270270;\n" +
        "  sum += texture2D(u_texture, v_texCoord0 + off1) * 0.3162162162;\n" +
        "  sum += texture2D(u_texture, v_texCoord0 - off1) * 0.3162162162;\n" +
        "  sum += texture2D(u_texture, v_texCoord0 + off2) * 0.0702702703;\n" +
        "  sum += texture2D(u_texture, v_texCoord0 - off2) * 0.0702702703;\n" +
        "  gl_FragColor = vec4(sum.rgb, sum.a * u_alpha);\n" +
        "}\n";

    public TriBlurBackground(){
        float w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        setSize(w, h);
        setPosition(0f, 0f);
        touchable = Touchable.disabled;
        // 截取打开瞬间的当前场景作为背景
        try{
            // 降采样截屏（1/4 尺寸）：放大渲染时 Linear 过滤自然产生强模糊（毛玻璃感）
            int fw = Math.max((int)(w / 4f), 64);
            int fh = Math.max((int)(h / 4f), 64);
            fbo = new FrameBuffer(fw, fh);
            fbo.resize(fw, fh);
            fboWidth = fw;
            fboHeight = fh;
            fbo.begin();
            Core.scene.draw();
            fbo.end();
            Log.info("TMail blur created, fbo ok, size=" + fw + "x" + fh);
        }catch(Throwable t){
            Log.err("TMail blur capture failed: " + t);
        }
    }

    @Override
    public void act(float delta){
        // 透明度逐渐加深
        alpha = Math.min(alpha + delta * ALPHA_SPEED, MAX_ALPHA);
    }

    @Override
    public void draw(){
        Draw.flush();
        Shader sh = blurShader();
        if(fbo == null || sh == null){
            // 兜底：即使截图/shader 失败，也渲染半透明黑背景（渐深），保证有背景效果
            Draw.color(0f, 0f, 0f, alpha * 0.6f);
            Tex.whiteui.draw(x, y, width, height);
            Draw.color();
            return;
        }
        if(!logged){
            logged = true;
            Log.info("TMail blur draw: fbo ok, shader ok, alpha=" + alpha);
        }
        // 必须先 bind shader 再设 uniform：arc 的 setUniformf 直接调 glUniform*，
        // 未绑定时（当前 program 为场景 batch 的 shader）会触发 GL_INVALID_OPERATION。
        sh.bind();
        // 采样坐标按 FBO 尺寸（降采样后的纹理坐标空间）
        sh.setUniformf("u_resolution", fboWidth, fboHeight);
        sh.setUniformf("u_alpha", alpha);
        Draw.blit(fbo, sh);
    }

    private static Shader blurShader(){
        if(blurShader == null && !shaderFailed){
            try{
                blurShader = new Shader(BLUR_VERT, BLUR_FRAG);
            }catch(Throwable t){
                shaderFailed = true;
                Log.err("TMail blur shader failed: " + t);
            }
        }
        return blurShader;
    }
}
