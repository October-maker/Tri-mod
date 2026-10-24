package triangle.content;

import arc.graphics.Color;
import arc.util.Time;
import mindustry.content.StatusEffects;
import mindustry.gen.Sounds;
import mindustry.type.Weather;
import mindustry.type.weather.ParticleWeather;
import mindustry.world.meta.Attribute;

/**
 * 三角模组的天气注册表。
 *
 * <p>目前仅包含粒子风暴 {@code sporestorm}：低温、强风、降低光照，并附带冻结状态，
 * 单次持续 10 分钟。
 */
public class TriWeather {

    /** 粒子风暴天气（内容名 {@code sporestorm}）。 */
    public static Weather storm;

    /** 创建天气内容（由 {@code triangle.loadContent()} 调用）。 */
    public static void load(){
        storm = new ParticleWeather("sporestorm"){{
            color = noiseColor = Color.valueOf("FFFFFF");
            particleRegion = "circle-small";
            drawNoise = true;
            statusGround = true;
            useWindVector = true;
            sizeMax = 8.7f;
            sizeMin = 5.5f;
            minAlpha = 0.5f;
            maxAlpha = 0.8f;
            density = 1600f;
            baseSpeed = 10.3f;
            xspeed = -6.2f;
            yspeed = 8.5f;
            attrs.set(Attribute.light, -0.15f);
            status = StatusEffects.freezing;
            opacityMultiplier = 0.5f;
            force = 8.8f;
            sound = Sounds.wind;
            soundVol = 1.2f;
            duration = 10f * Time.toMinutes;
        }};
    }
}
