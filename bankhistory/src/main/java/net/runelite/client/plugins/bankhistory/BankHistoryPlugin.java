/*
 * Copyright (c) 2020, Adrian Lee Elder <https://github.com/AdrianLeeElder>
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
package net.runelite.client.plugins.bankhistory;

import com.google.common.base.Strings;
import com.google.inject.Provides;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetHiddenChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import org.pf4j.Extension;

@Extension
@PluginDescriptor(
	name = "Bank Value Tracking",
	description = "Track the value of your bank over time",
	tags = {"bank", "value", "history", "tracking"},
	type = PluginType.UTILITY,
	enabledByDefault = false
)
@Slf4j
public class BankHistoryPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private BankValueHistoryTracker tracker;

	@Inject
	private ClientToolbar clientToolbar;

	private NavigationButton navButton;
	private BankHistoryPanel bankHistoryPanel;

	@Provides
	BankHistoryConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankHistoryConfig.class);
	}

	@Override
	protected void startUp()
	{
		final BufferedImage icon = ImageUtil.getResourceStreamFromClass(getClass(), "bank_logo.png");

		navButton = NavigationButton.builder()
			.tooltip("Bank Value History")
			.icon(icon)
			.priority(10)
			.build();

		setActivePanel("");
		clientToolbar.addNavigation(navButton);
	}


	private void setActivePanel(String username)
	{
		if (!Strings.isNullOrEmpty(username) || hasAccountData())
		{
			if (bankHistoryPanel == null)
			{
				log.trace("Setting the active panel to the bank history panel");
				bankHistoryPanel = injector.getInstance(BankHistoryPanel.class);
				bankHistoryPanel.init(username);
				setCurrentPanel(bankHistoryPanel);
			}
		}
		else
		{
			log.trace("Setting the active panel to the default panel");
			DefaultBankValuePanel panel = new DefaultBankValuePanel();
			panel.init();
			setCurrentPanel(panel);
		}
	}

	private void setCurrentPanel(PluginPanel pluginPanel)
	{
		if (navButton.getPanel() != pluginPanel)
		{
			PluginPanel panel = navButton.getPanel();
			if (panel == null)
			{
				navButton.setPanel(pluginPanel);
				return;
			}

			Container con = panel.getParent();

			if (con != null)
			{
				navButton.setPanel(pluginPanel);
				removeExistingPanel(con);
				con.add(pluginPanel, BorderLayout.NORTH);
				con.revalidate();
				con.repaint();
			}
		}
	}

	private void removeExistingPanel(Container con)
	{
		Component[] comps = con.getComponents();
		if (comps != null)
		{
			for (Component component : comps)
			{
				if (component instanceof PluginPanel)
				{
					log.trace("Removing plugin panel instance from container");
					con.remove(component);
				}
			}
		}
	}

	private boolean hasAccountData()
	{
		return !tracker.getAvailableUsers().isEmpty();
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onWidgetLoaded(final WidgetLoaded event) throws InvocationTargetException, InterruptedException
	{
		if (event.getGroupId() == WidgetID.BANK_GROUP_ID)
		{
			log.trace("Player opened the bank");
			SwingUtilities.invokeAndWait(() -> this.setActivePanel(client.getUsername()));
			if (isHistoryPanelActive())
			{
				bankHistoryPanel.setDatasetButton(true);
			}
		}
	}

	private boolean isHistoryPanelActive()
	{
		return bankHistoryPanel != null && navButton.getPanel() == bankHistoryPanel;
	}

	@Subscribe
	public void onWidgetHiddenChanged(WidgetHiddenChanged event)
	{
		Widget widget = event.getWidget();
		int group = WidgetInfo.TO_GROUP(widget.getId());

		//when bank interface is closed
		//probably a better way to do this >.>
		if (!widget.isHidden() && group == WidgetID.RESIZABLE_VIEWPORT_OLD_SCHOOL_BOX_GROUP_ID)
		{
			if (bankHistoryPanel != null)
			{
				bankHistoryPanel.setDatasetButton(false);
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.CONNECTION_LOST)
		{
			bankHistoryPanel.setDatasetButton(false);
		}
	}
}