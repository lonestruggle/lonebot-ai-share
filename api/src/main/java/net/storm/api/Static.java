package net.storm.api;

import net.storm.api.movement.IMovement;
import net.storm.api.movement.IWalker;
import net.storm.api.widgets.IBankWornItems;
import net.storm.api.widgets.IDialog;
import net.storm.api.widgets.IFriends;
import net.storm.api.widgets.IMinigames;
import net.storm.api.widgets.IPrayers;
import net.storm.api.widgets.IProduction;
import net.storm.api.widgets.ITabs;
import net.storm.api.widgets.IWidgets;

/**
 * Storm {@code net.storm.api.Static} service locator.
 * SDK registers widget implementations at class-init.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/Static.html">Storm Static</a>
 */
public class Static {

    private static volatile IWidgets widgets;
    private static volatile IDialog dialog;
    private static volatile ITabs tabs;
    private static volatile IFriends friends;
    private static volatile IPrayers prayers;
    private static volatile IProduction production;
    private static volatile IMinigames minigames;
    private static volatile IBankWornItems bankWornItems;
    private static volatile IMovement movement;
    private static volatile IWalker walker;

    public Static() {
    }

    public static IWidgets getWidgets() {
        return widgets;
    }

    public static IDialog getDialog() {
        return dialog;
    }

    public static ITabs getTabs() {
        return tabs;
    }

    public static IFriends getFriends() {
        return friends;
    }

    public static IPrayers getPrayers() {
        return prayers;
    }

    public static IProduction getProduction() {
        return production;
    }

    public static IMinigames getMinigames() {
        return minigames;
    }

    public static IBankWornItems getBankWornItems() {
        return bankWornItems;
    }

    public static IMovement getMovement() {
        return movement;
    }

    public static IWalker getWalker() {
        return walker;
    }

    public static void bindWidgets(IWidgets value) {
        widgets = value;
    }

    public static void bindDialog(IDialog value) {
        dialog = value;
    }

    public static void bindTabs(ITabs value) {
        tabs = value;
    }

    public static void bindFriends(IFriends value) {
        friends = value;
    }

    public static void bindPrayers(IPrayers value) {
        prayers = value;
    }

    public static void bindProduction(IProduction value) {
        production = value;
    }

    public static void bindMinigames(IMinigames value) {
        minigames = value;
    }

    public static void bindBankWornItems(IBankWornItems value) {
        bankWornItems = value;
    }

    public static void bindMovement(IMovement value) {
        movement = value;
    }

    public static void bindWalker(IWalker value) {
        walker = value;
    }
}
