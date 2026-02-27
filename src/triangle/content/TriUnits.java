//package triangle.content;
//
//import mindustry.ai.types.BuilderAI;
//import mindustry.ai.types.CommandAI;
//import mindustry.entities.bullet.BasicBulletType;
//import mindustry.type.UnitType;
//import mindustry.type.Weapon;
//
//public class TriUnits {
//    public static  UnitType sentinel;
//    public static void load(){
//        sentinel = new UnitType("sentinel"){{
//            controller = u -> u.team.isAI() ? new BuilderAI(true, 400f) : new CommandAI();
//            health = 120f;
//            speed = 7.5f;
//            accel = 0.08f;
//            drag = 0.4f;
//            rotateSpeed = 72f;
//            hitSize = 6f;
//            itemCapacity = 40;
//            mineSpeed = 1f;
//            flying = true;
//            mineTier = 3;
//            buildSpeed = 1f;
//            range = 140f;
//            weapons.add(new Weapon("Gun"){{
//                x = 0f;
//                y = 0f;
//                reload = 30;
//                rotate = false;
//                inaccuracy = 5;
//                alternate = false;
//                shoot.shotDelay = 10;
//                shoot.shots = 3;
//                bullet = new BasicBulletType(4.5f,30f){{
//                    buildingDamageMultiplier = 0.01f;
//                    width = 6;
//                    height = 8;
//                    lifetime = 30f;
//                    pierce = false;
//                }};
//            }});
//        }};
//    }
//}
