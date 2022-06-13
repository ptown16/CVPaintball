package org.cubeville.cvpaintball.paintball;

import org.cubeville.cvgames.vartypes.*;

public class PaintballTeam extends GameVariableTeam {
    public PaintballTeam() {
        super("PaintballTeam");
        addField("tps", new GameVariableList<>(GameVariableLocation.class));
        addField("loadout-team", new GameVariableString());
        addField("damaged-teams", new GameVariableList<>(GameVariableString.class));

    }

    @Override
    public Object itemString() {
        return null;
    }
}
