package triangle.content;

import arc.graphics.Color;
import arc.util.Time;
import mindustry.content.StatusEffects;
import mindustry.gen.Sounds;
import mindustry.type.Weather;
import mindustry.type.weather.ParticleWeather;
import mindustry.world.meta.Attribute;

public class TriWeather {
    public static Weather storm;
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
            density = 3200f;
            baseSpeed = 10.3f;
            xspeed = -6.2f;
            yspeed = 8.5f;
            attrs.set(Attribute.light, -0.15f);
            status = StatusEffects.freezing;
            opacityMultiplier = 0.5f;
            force = 4.8f;
            sound = Sounds.wind;
            soundVol = 1.2f;
            duration = 10f * Time.toMinutes;
        }};
    }
}
