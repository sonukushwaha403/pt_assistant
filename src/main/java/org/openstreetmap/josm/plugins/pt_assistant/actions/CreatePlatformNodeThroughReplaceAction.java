// License: GPL. For details, see LICENSE file.

package org.openstreetmap.josm.plugins.pt_assistant.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmData;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.RelationToChildReference;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.datatransfer.OsmTransferHandler;
import org.openstreetmap.josm.gui.datatransfer.PrimitiveTransferable;
import org.openstreetmap.josm.gui.datatransfer.data.PrimitiveTransferData;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.customizepublictransportstop.OSMTags;
import org.openstreetmap.josm.plugins.pt_assistant.PTAssistantPlugin;
import org.openstreetmap.josm.plugins.pt_assistant.utils.StopUtils;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.MultiMap;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Creates a platform node where the cursor is present, removes all the tags from the selected node and puts it in the new node.
 *
 * @author Biswesh
 *
 */
public class CreatePlatformNodeThroughReplaceAction extends JosmAction {

    protected final OsmTransferHandler transferHandler;

    /**
     * Creates a new PlatformAction
     */
    public CreatePlatformNodeThroughReplaceAction() {
        // CHECKSTYLE.OFF: LineLength
        super(
            tr("Shortcut action to Transfer details of stop to platform node"),
            null,
            tr("Shortcut action to Transfer details of stop to platform node"),
            Shortcut.registerShortcut("tools:createplatformthruoghreplace",
                tr("Shortcut action to Transfer details of stop to platform node"), KeyEvent.VK_G, Shortcut.SHIFT),
            false
        );
        transferHandler = new OsmTransferHandler();
        MainApplication.registerActionShortcut(
            this,
            Shortcut.registerShortcut("tools:createplatformthruoghreplace",
                tr("Shortcut action to Transfer details of stop to platform node"), KeyEvent.VK_G, Shortcut.SHIFT));
        // CHECKSTYLE.ON: LineLength
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final OsmDataLayer editLayer = getLayerManager().getEditLayer();
        if (editLayer != null) {
            final Optional<Node> stopPositionNode = Optional.ofNullable(editLayer.getDataSet()) // dataset
                .map(OsmData::getSelected) // selection
                .filter(it -> it.size() == 1) // only when exactly one primitive
                .map(it -> it.iterator().next()) // get first and only one
                .map(it -> it instanceof Node ? (Node) it : null) // only if it's a node
                .filter(StopUtils::isHighwayOrRailwayStopPosition); // only if it's a stop position
            if (stopPositionNode.isPresent()) {
                transferHandler.pasteOn(
                    editLayer,
                    computePastePosition(e),
                    new PrimitiveTransferable(PrimitiveTransferData.getDataWithReferences(Collections.singleton(stopPositionNode.get())))
                );

                Optional.ofNullable(editLayer.getDataSet())
                    .map(OsmData::getSelected)
                    .flatMap(newSelection ->
                        newSelection.stream()
                            .map(primitive -> primitive instanceof Node ? (Node) primitive : null)
                            .filter(Objects::nonNull)
                            .reduce((a, b) -> b) // equivalent to a `findLast()` method if that would exist
                    )
                    .ifPresent(node -> modify(node, stopPositionNode.get()));
            } else {
                new Notification(I18n.tr("This action can only be performed if exactly one stop position node is selected."))
                    .setIcon(PTAssistantPlugin.ICON.get())
                    .show();
            }
        }

        // try {
        // MainApplication.undoRedo.add(new DeleteCommand(stopPositionNode));
        // } catch (Exception f) {
        // f.printStackTrace();
        // }

    }

    protected EastNorth computePastePosition(ActionEvent e) {
        // default to paste in center of map (pasted via menu or cursor not in MapView)
        MapView mapView = MainApplication.getMap().mapView;
        EastNorth mPosition = mapView.getCenter();
        // We previously checked for modifier to know if the action has been trigerred
        // via shortcut or via menu
        // But this does not work if the shortcut is changed to a single key (see #9055)
        // Observed behaviour: getActionCommand() returns Action.NAME when triggered via
        // menu, but shortcut text when triggered with it
        if (e != null && !getValue(NAME).equals(e.getActionCommand())) {
            final PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            if (pointerInfo != null) {
                final Point mp = pointerInfo.getLocation();
                final Point tl = mapView.getLocationOnScreen();
                final Point pos = new Point(mp.x - tl.x, mp.y - tl.y);
                if (mapView.contains(pos)) {
                    mPosition = mapView.getEastNorth(pos.x, pos.y);
                }
            }
        }
        return mPosition;
    }

    public void modify(Node newNode, Node stopPositionNode) {

        if (stopPositionNode.hasTag(OSMTags.RAILWAY_TAG)) {
            newNode.put(OSMTags.TRAM_TAG, OSMTags.YES_TAG_VALUE);
            newNode.put(OSMTags.RAILWAY_TAG, OSMTags.TRAM_STOP_TAG_VALUE);
            newNode.remove(OSMTags.PUBLIC_TRANSPORT_TAG);
            newNode.put(OSMTags.PUBLIC_TRANSPORT_TAG, OSMTags.PLATFORM_TAG_VALUE);

            List<Command> commands = getReplaceGeometryCommand(stopPositionNode, newNode);
            if (!commands.isEmpty()) {
                UndoRedoHandler.getInstance().add(new SequenceCommand(tr("Replace Membership"), commands));
            }

            HashMap<String, String> tags = new HashMap<>(stopPositionNode.getKeys());
            tags.replaceAll((key, value) -> null);
            UndoRedoHandler.getInstance().add(new ChangePropertyCommand(Collections.singleton(stopPositionNode), tags));

        } else if (stopPositionNode.hasTag(OSMTags.HIGHWAY_TAG)) {
            newNode.put(OSMTags.BUS_TAG, OSMTags.YES_TAG_VALUE);
            newNode.put(OSMTags.HIGHWAY_TAG, OSMTags.BUS_STOP_TAG_VALUE);
            newNode.remove(OSMTags.PUBLIC_TRANSPORT_TAG);
            newNode.put(OSMTags.PUBLIC_TRANSPORT_TAG, OSMTags.PLATFORM_TAG_VALUE);

            List<Command> commands = getReplaceGeometryCommand(stopPositionNode, newNode);
            if (!commands.isEmpty()) {
                UndoRedoHandler.getInstance().add(new SequenceCommand(tr("Replace Membership"), commands));
            }

            HashMap<String, String> tags = new HashMap<>(stopPositionNode.getKeys());
            tags.replaceAll((key, value) -> null);
            UndoRedoHandler.getInstance().add(new ChangePropertyCommand(Collections.singleton(stopPositionNode), tags));
        }

    }

    static List<Command> getReplaceGeometryCommand(OsmPrimitive firstObject, OsmPrimitive secondObject) {
        final MultiMap<Relation, RelationToChildReference> byRelation = new MultiMap<>();
        for (final RelationToChildReference i : RelationToChildReference.getRelationToChildReferences(firstObject)) {
            byRelation.put(i.getParent(), i);
        }

        final List<Command> commands = new ArrayList<>();
        for (final Map.Entry<Relation, Set<RelationToChildReference>> i : byRelation.entrySet()) {
            final Relation oldRelation = i.getKey();
            final Relation newRelation = new Relation(oldRelation);
            for (final RelationToChildReference reference : i.getValue()) {
                newRelation.setMember(reference.getPosition(), new RelationMember(OSMTags.PLATFORM_ROLE, secondObject));
            }
            commands.add(new ChangeCommand(oldRelation, newRelation));
        }

        return commands;
    }

}
