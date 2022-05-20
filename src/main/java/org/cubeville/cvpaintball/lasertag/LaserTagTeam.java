package org.cubeville.cvpaintball.lasertag;

import org.cubeville.cvgames.vartypes.*;

public class LaserTagTeam extends GameVariableObject {
    public LaserTagTeam() {
        super("PaintballTeam");
        addField("name", new GameVariableString());
        addField("laser-gun", new GameVariableItem());
        addField("chat-color", new GameVariableChatColor());
        addField("armor-color", new GameVariableArmorColor());
        addField("tps", new GameVariableList<>(GameVariableLocation.class));
    }

    @Override
    public Object itemString() {
        return null;
    }
}
