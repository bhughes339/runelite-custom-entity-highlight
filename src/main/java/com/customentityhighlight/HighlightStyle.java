package com.customentityhighlight;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HighlightStyle
{
	OUTLINE("Outline"),
	HULL("Hull"),
	AREA("Area"),
	CLICKBOX("Clickbox"),
	TILE("Tile"),
	// The tile the server places an NPC on, without the client's walking animation; the same as Tile for objects
	TRUE_TILE("True tile"),
	// Draws nothing, so the group only shows labels
	NONE("None");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
