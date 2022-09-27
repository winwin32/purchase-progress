/*
 * Copyright (c) 2022, BrastaSauce
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.brastasauce.purchaseprogress;

import com.google.gson.Gson;
import com.google.inject.Provides;

import javax.inject.Inject;
import javax.swing.SwingUtilities;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@PluginDescriptor(
	name = "Purchase Progress"
)
public class PurchaseProgressPlugin extends Plugin
{
	public static final String CONFIG_GROUP = "purchaseprogress";
	private static final String PLUGIN_NAME = "Purchase Progress";
	private static final String ICON_IMAGE = "/panel_icon.png";

	@Getter
	@Setter
	private List<PurchaseProgressItem> items = new ArrayList<>();

	@Getter
	@Setter
	private long value = 0;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private BankCalculation bankCalculation;

	@Inject
	private Gson gson;

	@Inject
	private PurchaseProgressDataManager dataManager;

	@Inject
	private RuneLiteConfig runeLiteConfig;

	@Inject
	private Client client;

	@Inject
	private PurchaseProgressConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	private PurchaseProgressPluginPanel panel;
	private NavigationButton navButton;

	public void addItem(PurchaseProgressItem item)
	{
		clientThread.invokeLater(() ->
		{
			if (!containsItem(item))
			{
				items.add(item);
				dataManager.saveData();
				SwingUtilities.invokeLater(() ->
				{
					panel.switchToProgress();
					panel.updateProgressPanels();
				});
			}
			else
			{
				SwingUtilities.invokeLater(() -> panel.containsItemWarning());
			}
		});
	}

	public void removeItem(PurchaseProgressItem item)
	{
		clientThread.invokeLater(() -> {
			items.remove(item);
			dataManager.saveData();
			SwingUtilities.invokeLater(() -> panel.updateProgressPanels());
		});
	}

	@Schedule(
			period = 5,
			unit = ChronoUnit.MINUTES
	)
	public void updateItemPrices()
	{
		for (PurchaseProgressItem item : items)
		{
			item.setGePrice(itemManager.getItemPrice(item.getItemId()));
		}

		SwingUtilities.invokeLater(() -> panel.updateProgressPanels());
	}

	private boolean containsItem(PurchaseProgressItem newItem)
	{
		return items.contains(newItem);
	}

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(PurchaseProgressPluginPanel.class);

		final BufferedImage icon = ImageUtil.loadImageResource(PurchaseProgressPlugin.class, ICON_IMAGE);

		navButton = NavigationButton.builder()
				.tooltip(PLUGIN_NAME)
				.icon(icon)
				.priority(9)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		this.dataManager = new PurchaseProgressDataManager(this, configManager, itemManager, gson);

		clientThread.invokeLater(() ->
		{
			dataManager.loadData();
			updateItemPrices();
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
	}

	@Provides
	PurchaseProgressConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PurchaseProgressConfig.class);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_BUILD)
		{
			clientThread.invokeLater(() ->
			{
				value = bankCalculation.calculateValue();
				dataManager.saveData();
				SwingUtilities.invokeLater(() -> panel.updateProgressPanels());
			});
		}
	}
}
