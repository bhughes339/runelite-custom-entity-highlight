package com.customentityhighlight;

import com.google.common.base.Strings;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Area;
import java.util.BitSet;
import java.util.List;
import java.util.regex.MatchResult;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.Text;

class CustomEntityHighlightOverlay extends Overlay
{
	// Used by the area style when the fill color is fully transparent
	private static final int AREA_FALLBACK_ALPHA = 50;
	// How far above a model's top an Above label is placed, in local units
	private static final int LABEL_GAP = 40;

	private final Client client;
	private final CustomEntityHighlightPlugin plugin;
	private final ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	private CustomEntityHighlightOverlay(Client client, CustomEntityHighlightPlugin plugin,
		ModelOutlineRenderer modelOutlineRenderer)
	{
		this.client = client;
		this.plugin = plugin;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<HighlightGroup> groups = plugin.getGroups();
		if (groups.isEmpty())
		{
			return null;
		}

		GroupStyle[] styles = new GroupStyle[groups.size()];
		for (int i = 0; i < styles.length; i++)
		{
			styles[i] = new GroupStyle(groups.get(i));
		}

		for (NPC npc : plugin.getNpcs())
		{
			NPCComposition comp = npc.getTransformedComposition();
			BitSet matches = comp != null ? plugin.getMatchingGroups(npc, comp) : null;
			if (matches == null)
			{
				continue;
			}

			// The first group drawn that shows labels decides the label
			int labelGroup = -1;
			for (int i = matches.nextSetBit(0); i >= 0; i = matches.nextSetBit(i + 1))
			{
				GroupStyle style = styles[i];
				if (!style.group.isEnabled() || !style.group.isHighlightNpcs())
				{
					continue;
				}

				renderNpc(graphics, npc, style);
				if (labelGroup == -1 && style.group.isShowNames())
				{
					labelGroup = i;
				}
			}

			if (labelGroup != -1)
			{
				GroupStyle labelStyle = styles[labelGroup];
				MatchResult match = labelStyle.usesMatch ? plugin.getMatch(labelGroup, comp) : null;
				String name = label(labelStyle, comp.getName(), match);
				int zOffset = labelZOffset(labelStyle.labelPosition, npc.getLogicalHeight());
				renderName(graphics, npc.getCanvasTextLocation(graphics, name, zOffset), name, labelStyle);
			}
		}

		for (TileObject object : plugin.getObjects())
		{
			if (object.getPlane() != object.getWorldView().getPlane())
			{
				continue;
			}

			ObjectComposition comp = plugin.getCurrentComposition(object);
			BitSet matches = comp != null ? plugin.getMatchingGroups(comp) : null;
			if (matches == null)
			{
				continue;
			}

			// The first group drawn that shows labels decides the label
			int labelGroup = -1;
			for (int i = matches.nextSetBit(0); i >= 0; i = matches.nextSetBit(i + 1))
			{
				GroupStyle style = styles[i];
				if (!style.group.isEnabled() || !style.group.isHighlightObjects())
				{
					continue;
				}

				renderObject(graphics, object, style);
				if (labelGroup == -1 && style.group.isShowNames())
				{
					labelGroup = i;
				}
			}

			if (labelGroup != -1)
			{
				GroupStyle labelStyle = styles[labelGroup];
				MatchResult match = labelStyle.usesMatch ? plugin.getMatch(labelGroup, comp) : null;
				String name = label(labelStyle, comp.getName(), match);
				int zOffset = labelZOffset(labelStyle.labelPosition, getModelHeight(object));
				renderName(graphics, object.getCanvasTextLocation(graphics, name, zOffset), name, labelStyle);
			}
		}

		return null;
	}

	private void renderNpc(Graphics2D graphics, NPC npc, GroupStyle style)
	{
		switch (style.group.getStyleOrDefault())
		{
			case OUTLINE:
				modelOutlineRenderer.drawOutline(npc, style.width, style.color, style.feather);
				break;
			case HULL:
				renderShape(graphics, npc.getConvexHull(), style);
				break;
			case AREA:
				renderArea(graphics, npc.getConvexHull(), style.areaFill);
				break;
			case CLICKBOX:
				renderShape(graphics, getClickbox(npc), style);
				break;
			case TILE:
				renderShape(graphics, npc.getCanvasTilePoly(), style);
				break;
			case TRUE_TILE:
				renderShape(graphics, getTrueTilePoly(npc), style);
				break;
			case NONE:
				// Nothing to draw; the label, if any, is drawn separately
				break;
		}
	}

	private void renderObject(Graphics2D graphics, TileObject object, GroupStyle style)
	{
		switch (style.group.getStyleOrDefault())
		{
			case OUTLINE:
				modelOutlineRenderer.drawOutline(object, style.width, style.color, style.feather);
				break;
			case HULL:
				renderShape(graphics, getConvexHull(object), style);
				break;
			case AREA:
				renderArea(graphics, getConvexHull(object), style.areaFill);
				break;
			case CLICKBOX:
				renderShape(graphics, object.getClickbox(), style);
				break;
			case TILE:
			case TRUE_TILE:
				// Objects don't move, so their true tile is the tile they're drawn on
				renderShape(graphics, object.getCanvasTilePoly(), style);
				break;
			case NONE:
				// Nothing to draw; the label, if any, is drawn separately
				break;
		}
	}

	private Shape getClickbox(NPC npc)
	{
		LocalPoint lp = npc.getLocalLocation();
		WorldView worldView = npc.getWorldView();
		Model model = npc.getModel();
		if (lp == null || worldView == null || model == null)
		{
			return null;
		}

		return Perspective.getClickbox(client, worldView, model, npc.getCurrentOrientation(), lp.getX(), lp.getY(),
			Perspective.getTileHeight(client, lp, worldView.getPlane()));
	}

	/**
	 * Returns the area an NPC covers on its server-side tile, which its model can lag behind while it walks.
	 */
	private Shape getTrueTilePoly(NPC npc)
	{
		WorldView worldView = npc.getWorldView();
		if (worldView == null)
		{
			return null;
		}

		LocalPoint lp = LocalPoint.fromWorld(worldView, npc.getWorldLocation());
		if (lp == null)
		{
			return null;
		}

		// The world location is the NPC's south-west tile; the tile area is drawn around its center
		int size = npc.getComposition().getSize();
		int offset = Perspective.LOCAL_TILE_SIZE * (size - 1) / 2;
		return Perspective.getCanvasTileAreaPoly(client, lp.plus(offset, offset), size);
	}

	private static Shape getConvexHull(TileObject object)
	{
		if (object instanceof GameObject)
		{
			return ((GameObject) object).getConvexHull();
		}
		if (object instanceof GroundObject)
		{
			return ((GroundObject) object).getConvexHull();
		}
		// Walls and wall decorations can have two models, e.g. a diagonal wall's two halves
		if (object instanceof WallObject)
		{
			WallObject wall = (WallObject) object;
			return union(wall.getConvexHull(), wall.getConvexHull2());
		}
		if (object instanceof DecorativeObject)
		{
			DecorativeObject decoration = (DecorativeObject) object;
			return union(decoration.getConvexHull(), decoration.getConvexHull2());
		}
		return null;
	}

	private static Shape union(Shape a, Shape b)
	{
		if (a == null)
		{
			return b;
		}
		if (b == null)
		{
			return a;
		}

		Area area = new Area(a);
		area.add(new Area(b));
		return area;
	}

	private static void renderShape(Graphics2D graphics, Shape shape, GroupStyle style)
	{
		if (shape != null)
		{
			OverlayUtil.renderPolygon(graphics, shape, style.color, style.fill, style.stroke);
		}
	}

	private static void renderArea(Graphics2D graphics, Shape shape, Color fill)
	{
		if (shape != null)
		{
			graphics.setColor(fill);
			graphics.fill(shape);
		}
	}

	/**
	 * Returns the text to draw above an entity, falling back to its name when the chosen text is missing or blank.
	 */
	private static String label(GroupStyle style, String name, MatchResult match)
	{
		switch (style.labelSource)
		{
			case MATCH:
				if (match != null)
				{
					return match.group();
				}
				break;
			case CUSTOM:
				if (style.customLabel != null && !style.usesMatch)
				{
					return style.customLabel;
				}
				if (match != null)
				{
					String expanded = GroupMatcher.expandLabel(style.customLabel, match);
					if (!expanded.trim().isEmpty())
					{
						return expanded;
					}
				}
				break;
		}
		return Text.removeTags(name);
	}

	/**
	 * Returns how high above the ground to place a label, for a model of the given height.
	 */
	private static int labelZOffset(LabelPosition position, int modelHeight)
	{
		switch (position)
		{
			case MIDDLE:
				return modelHeight / 2;
			case BELOW:
				return 0;
			case ABOVE:
			default:
				return modelHeight + LABEL_GAP;
		}
	}

	/**
	 * Returns the height of an object's main model, or 0 if it has none.
	 */
	private static int getModelHeight(TileObject object)
	{
		Renderable renderable = null;
		if (object instanceof GameObject)
		{
			renderable = ((GameObject) object).getRenderable();
		}
		else if (object instanceof GroundObject)
		{
			renderable = ((GroundObject) object).getRenderable();
		}
		else if (object instanceof WallObject)
		{
			renderable = ((WallObject) object).getRenderable1();
		}
		else if (object instanceof DecorativeObject)
		{
			renderable = ((DecorativeObject) object).getRenderable();
		}
		return renderable != null ? renderable.getModelHeight() : 0;
	}

	private static void renderName(Graphics2D graphics, Point location, String name, GroupStyle style)
	{
		if (location == null)
		{
			return;
		}

		// The location is the text's baseline. Move it down so the text is centered on the point (Middle)
		// or hangs beneath it (Below), instead of sitting on top of it.
		int ascent = graphics.getFontMetrics().getAscent();
		switch (style.labelPosition)
		{
			case MIDDLE:
				location = new Point(location.getX(), location.getY() + ascent / 2);
				break;
			case BELOW:
				location = new Point(location.getX(), location.getY() + ascent);
				break;
			default:
				break;
		}
		OverlayUtil.renderTextLocation(graphics, location, name, style.color);
	}

	/**
	 * A group's drawing values, worked out once per frame.
	 */
	private static class GroupStyle
	{
		private final HighlightGroup group;
		private final Color color;
		private final Color fill;
		private final Color areaFill;
		private final int width;
		private final int feather;
		private final BasicStroke stroke;
		private final LabelSource labelSource;
		private final LabelPosition labelPosition;
		// Null when the custom label is blank
		private final String customLabel;
		// Whether the label is built from the group's match: Match labels, and Custom labels with $ references
		private final boolean usesMatch;

		private GroupStyle(HighlightGroup group)
		{
			this.group = group;
			color = group.getHighlightColorValue();
			fill = group.getFillColorValue();
			areaFill = fill.getAlpha() > 0 ? fill
				: new Color(color.getRed(), color.getGreen(), color.getBlue(), AREA_FALLBACK_ALPHA);
			width = group.getBorderWidth();
			feather = group.getOutlineFeather();
			stroke = new BasicStroke(width);
			labelSource = group.getLabelSourceOrDefault();
			labelPosition = group.getLabelPositionOrDefault();
			customLabel = Strings.emptyToNull(group.getCustomLabel());
			usesMatch = labelSource == LabelSource.MATCH
				|| (labelSource == LabelSource.CUSTOM && customLabel != null && GroupMatcher.hasLabelReferences(customLabel));
		}
	}
}
