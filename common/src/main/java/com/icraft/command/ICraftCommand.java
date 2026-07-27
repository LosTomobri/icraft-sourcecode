package com.icraft.command;

import com.icraft.server.PhoneServerHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ICraftCommand {

    private static final String[][] COLOR_SUGGESTIONS = {
        { "&0", "&0 — Negro"          },
        { "&1", "&1 — Azul oscuro"    },
        { "&2", "&2 — Verde oscuro"   },
        { "&3", "&3 — Cyan oscuro"    },
        { "&4", "&4 — Rojo oscuro"    },
        { "&5", "&5 — Violeta"        },
        { "&6", "&6 — Naranja/Dorado" },
        { "&7", "&7 — Gris claro"     },
        { "&8", "&8 — Gris oscuro"    },
        { "&9", "&9 — Azul"           },
        { "&a", "&a — Verde"          },
        { "&b", "&b — Cyan/Celeste"   },
        { "&c", "&c — Rojo"           },
        { "&d", "&d — Rosa/Magenta"   },
        { "&e", "&e — Amarillo"       },
        { "&f", "&f — Blanco"         },
        { "&l", "&l — Negrita"        },
        { "&o", "&o — Cursiva"        },
        { "&n", "&n — Subrayado"      },
        { "&m", "&m — Tachado"        },
        { "&r", "&r — Reset"          },
    };

    private static final SuggestionProvider<CommandSourceStack> COLOR_SUGGESTER =
        (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase();
            for (String[] entry : COLOR_SUGGESTIONS) {
                String code    = entry[0];
                String tooltip = entry[1];
                if (remaining.isEmpty() || remaining.endsWith("&")
                        || tooltip.toLowerCase().contains(remaining)) {
                    builder.suggest(completionFor(builder.getInput(), code),
                                    Component.literal(tooltip));
                }
            }
            return builder.buildFuture();
        };

    private static final SuggestionProvider<CommandSourceStack> PHOTO_SUGGESTER =
        (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase();
            PhoneServerHandler.listAdminPhotos(ctx.getSource().getServer())
                    .stream()
                    .filter(name -> name.toLowerCase().contains(remaining))
                    .forEach(name -> builder.suggest(name, Component.literal("📷 " + name)));
            return builder.buildFuture();
        };

    private static String completionFor(String fullInput, String code) {
        if (fullInput.endsWith("&")) {
            return fullInput.substring(fullInput.indexOf(' ', fullInput.indexOf("adminmessage")) + 1)
                   + code.substring(1);
        }
        String prefix = fullInput.contains("adminmessage ")
            ? fullInput.substring(fullInput.indexOf("adminmessage ") + "adminmessage ".length())
            : "";
        return prefix + code;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("icraft")
                .requires(src -> src.hasPermission(2))

                .then(Commands.literal("adminmessage")
                    .then(Commands.argument("texto", StringArgumentType.greedyString())
                        .suggests(COLOR_SUGGESTER)
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "texto");
                            String content = raw.replace('&', '§');
                            PhoneServerHandler.broadcastToGlobalGroup(
                                ctx.getSource().getServer(), PhoneServerHandler.SYSTEM_SENDER, content);
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("[iCraft] Enviado: " + content), false);
                            return 1;
                        })
                    )
                )

                .then(Commands.literal("sendphoto")
                    .then(Commands.argument("archivo", StringArgumentType.word())
                        .suggests(PHOTO_SUGGESTER)
                        .executes(ctx -> {
                            String filename = StringArgumentType.getString(ctx, "archivo").trim();
                            try {

                                PhoneServerHandler.broadcastAdminPhoto(
                                        ctx.getSource().getServer(), filename);

                                PhoneServerHandler.broadcastToGlobalGroup(
                                        ctx.getSource().getServer(),
                                        PhoneServerHandler.SYSTEM_SENDER,
                                        "§§PHOTO:" + filename);

                                ctx.getSource().sendSuccess(
                                    () -> Component.literal(
                                        "[iCraft] 📷 \"" + filename + "\" enviada al chat Global."),
                                    false);
                                return 1;

                            } catch (IllegalArgumentException e) {
                                ctx.getSource().sendFailure(
                                    Component.literal("[iCraft] ✗ " + e.getMessage()));
                                return 0;
                            }
                        })
                    )
                )

                .then(Commands.literal("clearchats")
                    .executes(ctx -> {
                        PhoneServerHandler.broadcastToGlobalGroup(
                                ctx.getSource().getServer(),
                                PhoneServerHandler.SYSTEM_SENDER,
                                "§§CLEARCHATS");
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("[iCraft] 🗑 Chats vaciados en todos los clientes."), false);
                        return 1;
                    })
                )

                .then(Commands.literal("images")
                    .then(Commands.literal("on")
                        .executes(ctx -> {
                            PhoneServerHandler.setGlobalImagesEnabled(ctx.getSource().getServer(), true);
                            ctx.getSource().sendSuccess(
                                () -> Component.translatable("icraft.command.images_enabled"), false);
                            return 1;
                        })
                    )
                    .then(Commands.literal("off")
                        .executes(ctx -> {
                            PhoneServerHandler.setGlobalImagesEnabled(ctx.getSource().getServer(), false);
                            ctx.getSource().sendSuccess(
                                () -> Component.translatable("icraft.command.images_disabled"), false);
                            return 1;
                        })
                    )
                )

                .then(Commands.literal("chat")
                    .then(Commands.literal("on")
                        .executes(ctx -> {
                            PhoneServerHandler.setVanillaChatEnabled(ctx.getSource().getServer(), true);
                            ctx.getSource().sendSuccess(
                                () -> Component.translatable("icraft.command.chat_enabled"), false);
                            return 1;
                        })
                    )
                    .then(Commands.literal("off")
                        .executes(ctx -> {
                            PhoneServerHandler.setVanillaChatEnabled(ctx.getSource().getServer(), false);
                            ctx.getSource().sendSuccess(
                                () -> Component.translatable("icraft.command.chat_disabled"), false);
                            return 1;
                        })
                    )
                )
        );
    }
}
