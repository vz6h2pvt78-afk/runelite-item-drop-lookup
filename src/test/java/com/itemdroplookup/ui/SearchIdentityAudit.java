package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Bounded offline search matrix; no game/client/player input. */
public final class SearchIdentityAudit
{
    public static void main(String[] args) throws Exception
    {
        Gson gson=new Gson();
        DropLookupService drops=new DropLookupService(gson);
        ItemSourceLookupService sources=new ItemSourceLookupService(gson);
        ItemPriceLookupService prices=new ItemPriceLookupService(gson);
        List<Map<String,Object>> rows=new ArrayList<>();
        for(String query:new String[]{"uncut","runepouch","rune pouch","Rune pouch note","dboots","d boots","bcp",
                "rune scim","r scim","hydrasclaw","zulrahscales","Zulrah's scales","Zulrah’s scales",
                "Amulet of glory","Amulet of glory (uncharged)","Amulet of glory(1)","Amulet of glory(4)","Amulet of glory(5)",
                "Dragon impling jar","Dragonstone","Uncut dragonstone","Dragonstone bolt tips",
                "Air rune","airrune","Air rune pack","airrunepack","Eye of newt","Eye of newt pack",
                "Prayer potion","prayerpotion","Prayer potion(1)","Prayer potion (4)","Prayer potion(5)",
                "Super restore","Super restore(4)","Super restore(9)","Blighted super restore(4)",
                "Antidote+","Antidote++","Antidote+(4)","Antidote++(4)","antivenom+","Anti-venom+(4)",
                "Dragon dagger","Dragon dagger(p)","Dragon dagger (p)","Dragon dagger(p+)","Dragon dagger (p+)",
                "Dragon dagger(p++)","dragon dagger p++","Rune spear(p)","Rune spear(p+)","Rune spear(p++)",
                "Rune platebody (noted)","Coins","coin","Ruby","Ruby amulet","Ruby amulet (u)","zzzz-no-item"})
        {
            ItemSearchSelection selection=new ItemSearchSelection(query,drops,sources,prices);
            Map<String,Object> row=new LinkedHashMap<>();row.put("query",query);row.put("choices",selection.choices());
            row.put("dropItems",selection.drops.stream().map(s->s.getItemName()).distinct().sorted().collect(Collectors.toList()));
            row.put("sourceItems",selection.sources.stream().map(s->s.getItemName()).distinct().sorted().collect(Collectors.toList()));
            row.put("dropCount",selection.drops.size());row.put("sourceCount",selection.sources.size());
            rows.add(row);
        }
        Files.write(Path.of(args[0]),gson.newBuilder().setPrettyPrinting().create().toJson(rows).getBytes(StandardCharsets.UTF_8));
        System.out.println("Audited "+rows.size()+" query identities");
    }
}
