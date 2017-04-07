// --------------------------------------------------------------------------------------------------
//  Copyright (c) 2016 Microsoft Corporation
//  
//  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
//  associated documentation files (the "Software"), to deal in the Software without restriction,
//  including without limitation the rights to use, copy, modify, merge, publish, distribute,
//  sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions:
//  
//  The above copyright notice and this permission notice shall be included in all copies or
//  substantial portions of the Software.
//  
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
//  NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
//  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
//  DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// --------------------------------------------------------------------------------------------------
package com.microsoft.Malmo.MissionHandlers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.microsoft.Malmo.MissionHandlerInterfaces.IObservationProducer;
import com.microsoft.Malmo.Schemas.DrawItem;
import com.microsoft.Malmo.Schemas.MissionInit;
import com.microsoft.Malmo.Schemas.ObservationFromNearbyEntities;
import com.microsoft.Malmo.Schemas.RangeDefinition;
import com.microsoft.Malmo.Utils.MinecraftTypeHelper;

public class ObservationFromNearbyEntitiesImplementation extends HandlerBase implements IObservationProducer
{
    private ObservationFromNearbyEntities oneparams;
    private int lastFiringTimes[];
    private int tickCount = 0;

    @Override
    public boolean parseParameters(Object params)
    {
        if (params == null || !(params instanceof ObservationFromNearbyEntities))
            return false;
        
        this.oneparams = (ObservationFromNearbyEntities)params;
        lastFiringTimes = new int[this.oneparams.getRange().size()];
        return true;
    }
    
    @Override
    public void writeObservationsToJSON(JsonObject json, MissionInit missionInit)
    {
        this.tickCount++;

        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;

        // Get all the currently loaded entities:
        List<?> entities = Minecraft.getMinecraft().theWorld.getLoadedEntityList();

        // Get the list of RangeDefinitions that need firing:
        List<RangeDefinition> rangesToFire = new ArrayList<RangeDefinition>();
        int index = 0;
        for (RangeDefinition rd : this.oneparams.getRange())
        {
            if (this.tickCount - this.lastFiringTimes[index] >= rd.getUpdateFrequency())
            {
                rangesToFire.add(rd);
                this.lastFiringTimes[index] = this.tickCount;
            }
            index++;
        }

        // Create a list of empty lists to populate:
        List<List<Entity>> entitiesInRange = new ArrayList<List<Entity>>();
        for (int i = 0; i < rangesToFire.size(); i++)
            entitiesInRange.add(new ArrayList<Entity>());

        // Populate all our lists according to which entities are in range:
        for (Object obj : entities)
        {
            if (obj instanceof Entity)
            {
                Entity e = (Entity)obj;
                index = 0;
                for (RangeDefinition rd : rangesToFire)
                {
                    if (Math.abs(e.posX - player.posX) < rd.getXrange().doubleValue() &&
                        Math.abs(e.posY - player.posY) < rd.getYrange().doubleValue() && 
                        Math.abs(e.posZ - player.posZ) < rd.getZrange().doubleValue())
                        {
                            // Belongs in this list:
                            entitiesInRange.get(index).add(e);
                        }
                    index++;
                }
            }
        }

        // Now build up a JSON array for each populated list:
        index = 0;
        for (List<Entity> entsInRangeList : entitiesInRange)
        {
            if (!entitiesInRange.isEmpty())
            {
                JsonArray arr = new JsonArray();
                for (Entity e : entsInRangeList)
                {
                    JsonObject jsent = new JsonObject();
                    if (e instanceof EntityOtherPlayerMP)
                    {
                        EntityOtherPlayerMP otherplayer = (EntityOtherPlayerMP)e;
                        addOtherPlayerProperties(otherplayer, jsent);
                    }
                    else
                    {
                        jsent.addProperty("yaw", e.rotationYaw);
                        jsent.addProperty("x", e.posX);
                        jsent.addProperty("y", e.posY);
                        jsent.addProperty("z", e.posZ);
                        jsent.addProperty("pitch", e.rotationPitch);
                    }
                    String name = e.getName();
                    if (e instanceof EntityItem)
                    {
                        ItemStack is = ((EntityItem)e).getEntityItem();
                        DrawItem di = MinecraftTypeHelper.getDrawItemFromItemStack(is);
                        if (di != null)
                        {
                            name = di.getType();
                            if (di.getColour() != null)
                                jsent.addProperty("colour", di.getColour().value());
                            if (di.getVariant() != null)
                                jsent.addProperty("variation",  di.getVariant().getValue());
                        }
                        jsent.addProperty("quantity", is.stackSize);
                    }
                    jsent.addProperty("name", name);
                    arr.add(jsent);
                }
                json.add(this.oneparams.getRange().get(index).getName(), arr);
                index++;
            }
        }
    }

    private static void addOtherPlayerProperties(EntityOtherPlayerMP player, JsonObject jsent)
    {
        // Are we in the dev environment or deployed?
        boolean devEnv = (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
        // We need to know, because the member name will either be obfuscated or not.
        // NOTE: obfuscated names may need updating if Forge changes - check Malmo\Minecraft\build\taskLogs\retromapsources.log
        try
        {
            String xMemberName = devEnv ? "otherPlayerMPX" : "field_71185_c";
            String yMemberName = devEnv ? "otherPlayerMPY" : "field_71182_d";
            String zMemberName = devEnv ? "otherPlayerMPZ" : "field_71183_e";
            String yawMemberName = devEnv ? "otherPlayerMPYaw" : "field_71180_f";
            String pitchMemberName = devEnv ? "otherPlayerMPPitch" : "field_71181_g";
            Field x, y, z, yaw, pitch;
            x = EntityOtherPlayerMP.class.getDeclaredField(xMemberName);
            x.setAccessible(true);
            y = EntityOtherPlayerMP.class.getDeclaredField(yMemberName);
            y.setAccessible(true);
            z = EntityOtherPlayerMP.class.getDeclaredField(zMemberName);
            z.setAccessible(true);
            yaw = EntityOtherPlayerMP.class.getDeclaredField(yawMemberName);
            yaw.setAccessible(true);
            pitch = EntityOtherPlayerMP.class.getDeclaredField(pitchMemberName);
            pitch.setAccessible(true);
            Double xobj = (Double)x.get(player);
            Double yobj = (Double)y.get(player);
            Double zobj = (Double)z.get(player);
            Double yawobj = (Double)yaw.get(player);
            Double pitchobj = (Double)pitch.get(player);

            if (xobj != null)
                jsent.addProperty("x", xobj);
            if (yobj != null)
                jsent.addProperty("y", yobj);
            if (zobj != null)
                jsent.addProperty("z", zobj);
            if (yawobj != null)
                jsent.addProperty("yaw", yawobj);
            if (pitchobj != null)
                jsent.addProperty("pitch", pitchobj);
        }
        catch (SecurityException e)
        {
            e.printStackTrace();
        }
        catch (IllegalAccessException e)
        {
            e.printStackTrace();
        }
        catch (IllegalArgumentException e)
        {
            e.printStackTrace();
        }
        catch (NoSuchFieldException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void prepare(MissionInit missionInit)
    {
    }

    @Override
    public void cleanup()
    {
    }
}
