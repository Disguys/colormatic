/*
 * Colormatic
 * Copyright (C) 2019  Thalia Nero
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.kvverti.colormatic.properties;

import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.dimension.DimensionType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The global color.json file. It's a monster.
 */
public class GlobalColorProperties {

    private static final Logger log = LogManager.getLogger();

    private final Map<ColoredParticle, HexColor> particle;
    private final Map<DimensionType, HexColor> dimensionFog;
    private final Map<DimensionType, HexColor> dimensionSky;
    private final int lilypad;
    private final Map<StatusEffect, HexColor> potions;
    private final Map<DyeColor, HexColor> sheep;
    private final Map<DyeColor, float[]> sheepRgb;
    private final Map<DyeColor, HexColor> collar;
    private final Map<DyeColor, float[]> collarRgb;
    private final Map<DyeColor, HexColor> banner;

    private GlobalColorProperties(Settings settings) {
        this.particle = settings.particle;
        this.dimensionFog = convertMap(settings.fog, Registry.DIMENSION);
        this.dimensionSky = convertMap(settings.sky, Registry.DIMENSION);
        this.lilypad = settings.lilypad != null ? settings.lilypad.get() : 0;
        this.potions = convertMap(settings.potion, Registry.STATUS_EFFECT);
        this.sheep = settings.sheep;
        this.sheepRgb = toRgb(settings.sheep);
        this.collar = settings.collar;
        this.collarRgb = toRgb(settings.collar);
        this.banner = settings.map;
        // water potions' color does not correspond to a status effect
        // so we use `null` for the key
        HexColor water = settings.potion.get("water");
        if(water == null) {
            water = settings.potion.get("minecraft:water");
        }
        if(water != null) {
            this.potions.put(null, water);
        }
    }

    private static <T> Map<T, HexColor> convertMap(Map<String, HexColor> initial, Registry<T> registry) {
        Map<T, HexColor> res = new HashMap<>();
        for(Map.Entry<String, HexColor> entry : initial.entrySet()) {
            T key = registry.get(new Identifier(entry.getKey()));
            if(key != null) {
                res.put(key, entry.getValue());
            }
        }
        return res;
    }

    private static <T> Map<T, float[]> toRgb(Map<T, HexColor> map) {
        Map<T, float[]> res = new HashMap<>();
        for(Map.Entry<T, HexColor> entry : map.entrySet()) {
            int col = entry.getValue().get();
            float[] rgb = new float[3];
            rgb[0] = ((col >> 16) & 0xff) / 255.0f;
            rgb[1] = ((col >>  8) & 0xff) / 255.0f;
            rgb[2] = ((col >>  0) & 0xff) / 255.0f;
            res.put(entry.getKey(), rgb);
        }
        return res;
    }

    private static <T> int getColor(T key, Map<T, HexColor> map) {
        HexColor col = map.get(key);
        return col != null ? col.get() : 0;
    }

    public int getParticle(ColoredParticle part) {
        return getColor(part, particle);
    }

    public int getDimensionFog(DimensionType type) {
        return getColor(type, dimensionFog);
    }

    public int getDimensionSky(DimensionType type) {
        return getColor(type, dimensionSky);
    }

    public int getLilypad() {
        return lilypad;
    }

    public int getPotion(StatusEffect effect) {
        return getColor(effect, potions);
    }

    public int getWool(DyeColor color) {
        return getColor(color, sheep);
    }

    public float[] getWoolRgb(DyeColor color) {
        return sheepRgb.get(color);
    }

    public int getCollar(DyeColor color) {
        return getColor(color, collar);
    }

    public float[] getCollarRgb(DyeColor color) {
        return collarRgb.get(color);
    }

    public int getBanner(DyeColor color) {
        return getColor(color, banner);
    }

    public enum ColoredParticle implements StringIdentifiable {
        WATER("water"),
        PORTAL("portal");

        private final String name;

        private ColoredParticle(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }
    }

    // not sure if Optifine changed their color.properties keys over to
    // the official string IDs yet, but in case they haven't here they are
    private static final Map<String, String> keyRemap;
    static {
        keyRemap = new HashMap<>();
        keyRemap.put("nether", "the_nether");
        keyRemap.put("end", "the_end");
        keyRemap.put("lightBlue", "light_blue");
        keyRemap.put("silver", "light_gray");
        keyRemap.put("moveSpeed", "speed");
        keyRemap.put("moveSlowdown", "slowness");
        keyRemap.put("digSpeed", "haste");
        keyRemap.put("digSlowDown", "mining_fatigue");
        keyRemap.put("damageBoost", "strength");
        keyRemap.put("heal", "instant_health");
        keyRemap.put("harm", "instant_damage");
        keyRemap.put("jump", "jump_boost");
        keyRemap.put("confusion", "nausea");
        keyRemap.put("fireResistance", "fire_resistance");
        keyRemap.put("waterBreathing", "water_breathing");
        keyRemap.put("nightVision", "night_vision");
        keyRemap.put("healthBoost", "health_boost");
    }

    public static GlobalColorProperties load(ResourceManager manager, Identifier id, boolean fall) {
        try(Resource rsc = manager.getResource(id); InputStream in = rsc.getInputStream()) {
            try(Reader r = PropertyUtil.getJsonReader(in, id, k -> keyRemap.getOrDefault(k, k), k -> false)) {
                return loadFromJson(r, id);
            }
        } catch(IOException e) {
            return fall ? loadFromJson(new StringReader("{}"), id) : null;
        }
    }

    private static GlobalColorProperties loadFromJson(Reader rd, Identifier id) {
        Settings settings;
        try {
            settings = PropertyUtil.PROPERTY_GSON.fromJson(rd, Settings.class);
            if(settings == null) {
                settings = new Settings();
            }
        } catch(JsonParseException e) {
            log.error("Error parsing {}: {}", id, e.getMessage());
            settings = new Settings();
        }
        return new GlobalColorProperties(settings);
    }

    private static class Settings {
        // Some of the maps use string keys because the keys are identifiers
        // for registry elements. Referencing registry elements from mods not
        // present at runtime results in a null key. Multiple null keys result
        // in an exception from GSON, so we delay resolving identifiers until
        // construction when we can handle missing registry elements ourselves.
        Map<ColoredParticle, HexColor> particle = Collections.emptyMap();
        Map<String, HexColor> fog = Collections.emptyMap();
        Map<String, HexColor> sky = Collections.emptyMap();
        HexColor lilypad;
        Map<String, HexColor> potion = Collections.emptyMap();
        Map<DyeColor, HexColor> sheep = Collections.emptyMap();
        Map<DyeColor, HexColor> collar = Collections.emptyMap();
        Map<DyeColor, HexColor> map = Collections.emptyMap();
    }
}
