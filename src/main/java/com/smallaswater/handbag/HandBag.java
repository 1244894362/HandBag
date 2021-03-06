package com.smallaswater.handbag;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.form.element.ElementInput;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.item.Item;
import cn.nukkit.level.Sound;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.RemoveEntityPacket;
import cn.nukkit.plugin.PluginBase;
import com.smallaswater.handbag.inventorys.BagInventory;
import com.smallaswater.handbag.inventorys.BigBagInventory;
import com.smallaswater.handbag.items.BaseBag;
import com.smallaswater.handbag.items.types.BagType;


import java.util.LinkedHashMap;
import java.util.LinkedList;


/**
 * @author SmallasWater
 */
public class HandBag extends PluginBase implements Listener {


    private static HandBag bag;


    private LinkedHashMap<String,Long> key = new LinkedHashMap<>();

    private LinkedHashMap<String,Integer> slot = new LinkedHashMap<>();


    @Override
    public void onEnable() {
        bag = this;
        this.saveDefaultConfig();
        this.reloadConfig();
        this.getLogger().info("手提包加载成功...");
        this.register();
    }

    private void register() {
        LinkedList<BaseBag> baseBag = BaseBag.registerItem(getConfig());
        if (baseBag != null) {
            for(BaseBag baseBag1:baseBag){
                if (!Item.isCreativeItem(baseBag1.getItem())) {
                    Item.addCreativeItem(baseBag1.getItem());
                }
            }
            this.getServer().getPluginManager().registerEvents(this, this);
        }
    }

    public static HandBag getBag() {
        return bag;
    }

    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent event){
        Player player = event.getPlayer();
        Item hand = event.getItem();
        if(event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_AIR
                || event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK){
            if(hand.getNamedTag() != null){
                if(hand.getNamedTag().contains(BaseBag.NAME_TAG)){
                    event.setCancelled();
                    if(hand.getCount() > 1){
                        player.sendMessage("§6[§7手提袋§6] §c 堆叠状态无法开启");
                        return;
                    }
                    if(player.isSneaking()){
                        FormWindowCustom simple = new FormWindowCustom("重命名");
                        simple.addElement(new ElementInput("请输入新的名称"));
                        player.showFormWindow(simple,0x25565);
                        return;
                    }
                    BaseBag bag = BaseBag.getBaseBagByItem(hand.getNamedTag().getString("configName"),hand);
                    if(bag == null){
                        return;
                    }
                    BaseInventory inventory = bag.getInventory();
                    if(bag.getType() == BagType.SMALL){
                        player.addWindow(sendInventory(player, 98, inventory));
                    }else if (bag.getType() == BagType.TO_SMALL){
                        player.addWindow(sendInventory(player,96,inventory));
                    }else{
                        long id = Entity.entityCount++;
                        ((BigBagInventory) inventory).id = id;
                        key.put(player.getName(),id);
                        slot.put(player.getName(),player.getInventory().getHeldItemIndex());
                        player.level.addSound(player, Sound.RANDOM_CHESTOPEN);
                        player.addWindow(inventory);
                    }
                }
            }
        }
    }



    private BaseInventory sendInventory(Player player, int type, BaseInventory inventory){
        long id = Entity.entityCount++;
        AddEntityPacket fakeEntity = new AddEntityPacket();
        fakeEntity.entityUniqueId = id;
        fakeEntity.entityRuntimeId = id;
        fakeEntity.type = type;
        fakeEntity.x = (float)player.x;
        fakeEntity.y = (float)((player.y+3)>256 ? 256 : player.y+3);
        fakeEntity.z = (float)player.z;
        if(type == 98) {
            fakeEntity.metadata.putString(4, inventory.getTitle())
                    .putByte(44, 10).putInt(45, inventory.getSize());
        }else{
            fakeEntity.metadata.putString(4,  inventory.getTitle())
                    .putByte(44, 8).putInt(45, InventoryType.HOPPER.getDefaultSize());
        }
        player.dataPacket(fakeEntity);
        ((BagInventory) inventory).id = id;

        key.put(player.getName(),id);
        slot.put(player.getName(),player.getInventory().getHeldItemIndex());
        player.level.addSound(player, Sound.RANDOM_CHESTOPEN);
        return inventory;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event){
        if(event.getInventory() instanceof com.smallaswater.handbag.inventorys.BaseInventory){
            Player player = event.getPlayer();
            InventoryHolder holder = event.getInventory().getHolder();
            if(holder instanceof BaseBag) {
                player.level.addSound(player, Sound.RANDOM_CHESTCLOSED);
                Item item = ((BaseBag) holder).toItem();
                int slot = this.slot.get(player.getName());
                this.slot.remove(player.getName());
                if(holder.getInventory().getContents().size() == 0) {
                    if (((BaseBag) holder).isCanRemove()) {
                        Item r = player.getInventory().getItem(slot);
                        player.getInventory().remove(r);
                        return;
                    }
                }
                player.getInventory().setItem(slot,item);
            }
            this.slot.remove(player.getName());
            if(key.containsKey(player.getName())){
                RemoveEntityPacket pk = new RemoveEntityPacket();
                pk.eid = key.get(player.getName());
                player.dataPacket(pk);
                key.remove(player.getName());
            }
        }

    }



    @EventHandler
    public void onWindow(PlayerFormRespondedEvent event){
        Player player = event.getPlayer();
        if(event.getFormID() == 0x25565){
            if (event.getResponse() == null) {
                return;
            }
            FormWindowCustom custom = ((FormWindowCustom)event.getWindow());
            String input = custom.getResponse().getInputResponse(0);
            Item item = player.getInventory().getItemInHand();
            if(item.getNamedTag() != null){
                if(item.getNamedTag().contains(BaseBag.NAME_TAG)){
                    if("".equalsIgnoreCase(input)) {
                        input = "未命名手提袋";
                    }
                    item.setCustomName(input);
                    player.getInventory().setItemInHand(item);
                    player.sendMessage("§6[§7手提袋§6] §2 成功重命名为: §r"+input);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        Player player = event.getPlayer();
        if(key.containsKey(player.getName())){
            RemoveEntityPacket pk = new RemoveEntityPacket();
            pk.eid = key.get(player.getName());
            player.dataPacket(pk);
            key.remove(player.getName());
            if(slot.containsKey(player.getName())){
                player.getInventory().remove(player.getInventory()
                        .getItem(slot.get(player.getName())));
                slot.remove(player.getName());
            }
        }
    }


    @EventHandler
    public void onItemChange(InventoryTransactionEvent event){
        InventoryTransaction transaction = event.getTransaction();
        for(InventoryAction action:transaction.getActions()){
            Item item =  action.getSourceItem();
            if(item.getNamedTag() != null){
                if(item.getNamedTag().contains(BaseBag.NAME_TAG)){
                    for(Inventory inventory:transaction.getInventories()){
                        if(inventory instanceof com.smallaswater.handbag.inventorys.BaseInventory){
                            event.setCancelled();
                            return;
                        }
                        if(inventory instanceof PlayerInventory){
                            InventoryHolder player = inventory.getHolder();
                            if(player instanceof Player){
                                if(key.containsKey(((Player) player).getName())){
                                    event.setCancelled();
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }


}
