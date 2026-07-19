package com.icraft.command;

import com.icraft.server.PhoneServerHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Comandos del mod iCraft.
 *
 * /icraft adminmessage <texto>
 *   Envía un mensaje al chat global como "Sistema". Solo ops (nivel 2+).
 *   Soporta códigos de color &a, &b, etc. con autocompletado.
 *
 * /icraft sendphoto <archivo.png>
 *   Lee un PNG de iCraft/admin_photos/ en el servidor, lo distribuye a todos
 *   los clientes online vía AdminPhotoPacket (Base64) y luego broadcastea el
 *   mensaje §§PHOTO:<archivo> al chat Global para que aparezca en el celular.
 *   Solo ops (nivel 2+). Autocompletado con los .png disponibles en admin_photos/.
 *
 * /icraft clearchats
 *   Broadcastea §§CLEARCHATS al grupo Global como "Sistema".
 *   Cada cliente que recibe este mensaje especial llama a
 *   PhoneScreen.clearAllChats(), que vacía los mensajes de todas las
 *   conversaciones locales (sin borrar los chats en sí). Solo ops (nivel 2+).
 *   La operación ocurre ÚNICAMENTE en el cliente — el servidor no guarda
 *   historial, así que no hay nada que borrar del lado servidor.
 *
 * == FLUJO DE sendphoto ==
 *   1. Op sube un PNG a <servidor>/iCraft/admin_photos/
 *   2. Op ejecuta /icraft sendphoto cartel.png
 *   3. Servidor lee el PNG, lo codifica en Base64, envía AdminPhotoPacket a todos
 *   4. Cada cliente recibe el paquete, guarda el PNG en iCraft/photos/ y carga
 *      la textura en el TextureManager (PhoneScreen.receiveAdminPhoto)
 *   5. Servidor broadcastea "§§PHOTO:cartel.png" al grupo Global como "Sistema"
 *   6. El renderizador de burbujas muestra la miniatura de la imagen
 */
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

    /**
     * Autocompleta con los .png disponibles en iCraft/admin_photos/ del servidor.
     */
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

                // ── /icraft adminmessage <texto> ───────────────────────────
                .then(Commands.literal("adminmessage")
                    .then(Commands.argument("texto", StringArgumentType.greedyString())
                        .suggests(COLOR_SUGGESTER)
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "texto");
                            String content = raw.replace('&', '§');
                            PhoneServerHandler.broadcastToGlobalGroup(
                                ctx.getSource().getServer(), "§aSistema", content);
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("[iCraft] Enviado: " + content), false);
                            return 1;
                        })
                    )
                )

                // ── /icraft clearchats ─────────────────────────────────────
                // Broadcastea §§CLEARCHATS al grupo Global; cada cliente vacía
                // sus conversaciones localmente (solo efecto en cliente).
                .then(Commands.literal("clearchats")
                    .executes(ctx -> {
                        PhoneServerHandler.broadcastToGlobalGroup(
                                ctx.getSource().getServer(),
                                "§aSistema",
                                "§§CLEARCHATS");
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("[iCraft] 🗑 Chats vaciados en todos los clientes."), false);
                        return 1;
                    })
                )

                // ── /icraft sendphoto <archivo.png> ───────────────────────
                // Lee el PNG de iCraft/admin_photos/, lo manda a todos los clientes
                // como AdminPhotoPacket (Base64) y luego broadcastea §§PHOTO al Global.
                .then(Commands.literal("sendphoto")
                    .then(Commands.argument("archivo", StringArgumentType.word())
                        .suggests(PHOTO_SUGGESTER)
                        .executes(ctx -> {
                            String filename = StringArgumentType.getString(ctx, "archivo").trim();
                            try {
                                // 1. Distribuir el PNG a todos los clientes
                                PhoneServerHandler.broadcastAdminPhoto(
                                        ctx.getSource().getServer(), filename);

                                // 2. Broadcastear el mensaje de foto al grupo Global.
                                //    TCP garantiza orden: AdminPhotoPacket llega antes
                                //    que ChatMessagePacket, así la textura ya está lista
                                //    cuando el cliente procesa §§PHOTO.
                                PhoneServerHandler.broadcastToGlobalGroup(
                                        ctx.getSource().getServer(),
                                        "§aSistema",
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
        );
    }
}
