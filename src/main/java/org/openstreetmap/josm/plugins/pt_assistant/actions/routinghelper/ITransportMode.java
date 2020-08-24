package org.openstreetmap.josm.plugins.pt_assistant.actions.routinghelper;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

public interface ITransportMode {
    /**
     * Just a convenience method for {@link #canTraverseWay(IWay, WayTraversalDirection)} that assumes {@link WayTraversalDirection#FORWARD}
     * @param way the way for which we check, if it can be traversed by the transport mode
     * @return {@code true} if the transport mode can travel along the way in the forward direction. Otherwise {@code false}.
     */
    default boolean canTraverseWay(final Way way) {
        return canTraverseWay(way, WayTraversalDirection.FORWARD);
    }

    /**
     * @param way the way that is checked, if the transport mode can traverse
     * @param direction the travel direction for which we check
     * @return {@code true} iff the transport mode can travel along the given way in the given direction. Otherwise {@code false}.
     */
    boolean canTraverseWay(@NotNull IWay<?> way, @NotNull WayTraversalDirection direction);

    /**
     * Checks if this transport mode should be used for the given relation
     * @param relation the relation that is checked, if it is suitable for the transport mode
     * @return {@code true} if the transport mode is suitable for the relation. Otherwise {@code false}.
     */
    boolean canBeUsedForRelation(@NotNull final IRelation<?> relation);

    /**
     * @param from the way from which the vehicle is coming
     * @param via the node that the vehicle travels through, must be part of {@code from} and {@code to} ways,
     *            or this method will return false
     * @param to the way onto which the vehicle makes the turn
     * @return {@code true} iff the transport mode can make a turn from the given {@code from} way,
     *         via the given {@code via} node to the given {@code to} way. Otherwise {@code false}.
     *         This method assumes that both ways can be traversed by the transport mode, it does not check that.
     */
    boolean canTurn(@NotNull final Way from, @NotNull final Node via, @NotNull final Way to);
}
