package org.cubeville.cvpaintball.paintball;

import org.cubeville.cvgames.vartypes.*;

public class PaintballTeam extends GameVariableObject {
    public PaintballTeam() {
        super("PaintballTeam");
        addField("name", new GameVariableString());
        addField("snowball-name", new GameVariableString());
        addField("chat-color", new GameVariableChatColor());
        addField("armor-color", new GameVariableArmorColor());
        addField("armor-color-damaged", new GameVariableArmorColor());
        addField("tps", new GameVariableList<>(GameVariableLocation.class));
    }

    @Override
    public Object itemString() {
        return null;
    }
}
