package org.cubeville.cvpaintball.paintball;

import org.cubeville.cvgames.vartypes.*;

public class PaintballTeam extends GameVariableObject {
    public PaintballTeam() {
        super("PaintballTeam");
        addField("name", new GameVariableString());
        addField("chat-color", new GameVariableChatColor());
        addField("tps", new GameVariableList<>(GameVariableLocation.class));
        addField("loadout-team", new GameVariableString());
    }

    @Override
    public Object itemString() {
        return null;
    }
}
