package net.mca.client.gui;

import net.mca.Config;
import net.mca.MCA;
import net.mca.MCAClient;
import net.mca.ProfessionsMCA;
import net.mca.client.gui.widget.*;
import net.mca.client.resources.ClientUtils;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.Gender;
import net.mca.entity.ai.relationship.Personality;
import net.mca.network.c2s.GetVillagerRequest;
import net.mca.network.c2s.SkinListRequest;
import net.mca.network.c2s.VillagerEditorSyncRequest;
import net.mca.network.c2s.VillagerNameRequest;
import net.mca.resources.data.skin.Clothing;
import net.mca.resources.data.skin.Hair;
import net.mca.resources.data.skin.SkinListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.registry.Registry;
import net.minecraft.village.VillagerProfession;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class VillagerEditorScreen extends Screen implements SkinListUpdateListener {
    final UUID villagerUUID;
    final UUID playerUUID;
    final boolean allowPlayerModel;
    final boolean allowVillagerModel;
    private int villagerBreedingAge;
    protected String page;
    protected final VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.get().create(MinecraftClient.getInstance().world));
    protected final VillagerEntityMCA villagerVisualization = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.get().create(MinecraftClient.getInstance().world));
    protected static final int DATA_WIDTH = 175;
    private int traitPage = 0;
    private static final int TRAITS_PER_PAGE = 8;
    protected NbtCompound villagerData;
    private TextFieldWidget villagerNameField;
    private boolean hsvColoredHair;
    private final ColorSelector color = new ColorSelector();

    private int clothingPage;
    private int clothingPageCount;
    private ButtonWidget pageButtonWidget;

    private List<String> filteredClothing = new LinkedList<>();
    private List<String> filteredHair = new LinkedList<>();
    private static boolean isSkinListOutdated = true;
    private static HashMap<String, Clothing> clothing = new HashMap<>();
    private static HashMap<String, Hair> hair = new HashMap<>();
    private Gender filterGender = Gender.NEUTRAL;
    private String searchString = "";
    private int hoveredClothingId;

    final int CLOTHES_H = 8;
    final int CLOTHES_V = 2;
    final int CLOTHES_PER_PAGE = CLOTHES_H * CLOTHES_V + 1;

    ButtonWidget widgetMasculine;
    ButtonWidget widgetFeminine;

    private ButtonWidget villagerSkinWidget;
    private ButtonWidget playerSkinWidget;
    private ButtonWidget vanillaSkinWidget;
    private ButtonWidget doneWidget;

    public VillagerEditorScreen(UUID villagerUUID, UUID playerUUID, boolean allowPlayerModel, boolean allowVillagerModel) {
        super(Text.translatable("gui.VillagerEditorScreen.title"));
        this.villagerUUID = villagerUUID;
        this.playerUUID = playerUUID;
        this.allowPlayerModel = allowPlayerModel;
        this.allowVillagerModel = allowVillagerModel;

        requestVillagerData();
        setPage(Objects.requireNonNullElse(page, "loading"));
    }

    public VillagerEditorScreen(UUID villagerUUID, UUID playerUUID) {
        this(villagerUUID, playerUUID, MCAClient.isPlayerRendererAllowed(), MCAClient.isVillagerRendererAllowed());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void init() {
        setPage(page);
    }

    private int doubleGeneSliders(int y, Genetics.GeneType... genes) {
        boolean right = false;
        Genetics genetics = villager.getGenetics();
        for (Genetics.GeneType g : genes) {
            addDrawableChild(new GeneSliderWidget(width / 2 + (right ? DATA_WIDTH / 2 : 0), y, DATA_WIDTH / 2, 20, Text.translatable(g.getTranslationKey()), genetics.getGene(g), b -> genetics.setGene(g, b.floatValue())));
            if (right) {
                y += 20;
            }
            right = !right;
        }
        return y + 4 + (right ? 20 : 0);
    }

    private int integerChanger(int y, IntConsumer onClick, Supplier<Text> content) {
        int bw = 22;
        ButtonWidget current = addDrawableChild(new ButtonWidget(width / 2 + bw * 2, y, DATA_WIDTH - bw * 4, 20, content.get(), b -> {
        }));
        addDrawableChild(new ButtonWidget(width / 2, y, bw, 20, Text.literal("-5"), b -> {
            onClick.accept(-5);
            current.setMessage(content.get());
        }));
        addDrawableChild(new ButtonWidget(width / 2 + bw, y, bw, 20, Text.literal("-50"), b -> {
            onClick.accept(-50);
            current.setMessage(content.get());
        }));
        addDrawableChild(new ButtonWidget(width / 2 + DATA_WIDTH - bw * 2, y, bw, 20, Text.literal("+50"), b -> {
            onClick.accept(50);
            current.setMessage(content.get());
        }));
        addDrawableChild(new ButtonWidget(width / 2 + DATA_WIDTH - bw, y, bw, 20, Text.literal("+5"), b -> {
            onClick.accept(5);
            current.setMessage(content.get());
        }));
        return y + 22;
    }

    protected void setPage(String page) {
        this.page = page;

        clearChildren();

        if (page.equals("loading")) {
            return;
        }

        //page selection
        if (shouldShowPageSelection()) {
            String[] pages = getPages();
            int w = DATA_WIDTH * 2 / pages.length;
            int x = (int) (width / 2.0 - pages.length / 2.0 * w);
            for (String p : pages) {
                addDrawableChild(new ButtonWidget(x, height / 2 - 105, w, 20, Text.translatable("gui.villager_editor.page." + p), sender -> setPage(p))).active = !p.equals(page);
                x += w;
            }

            //close
            doneWidget = addDrawableChild(new ButtonWidget(width / 2 - DATA_WIDTH + 20, height / 2 + 85, DATA_WIDTH - 40, 20, Text.translatable("gui.done"), sender -> {
                syncVillagerData();
                close();
            }));
        }

        int y = height / 2 - 80;
        int margin = 40;
        Genetics genetics = villager.getGenetics();
        TextFieldWidget textFieldWidget;

        switch (page) {
            case "general" -> {
                //name
                drawName(width / 2, y);
                y += 20;

                //gender
                drawGender(width / 2, y);
                y += 22;

                if (villagerUUID.equals(playerUUID)) {
                    addModelSelectionWidgets(width / 2, y);
                    y += 22;
                }

                //age
                if (!villagerUUID.equals(playerUUID)) {
                    addDrawableChild(new GeneSliderWidget(width / 2, y, DATA_WIDTH, 20, Text.translatable("gui.villager_editor.age"), 1.0 + villagerBreedingAge / (double) AgeState.getMaxAge(), b -> {
                        villagerBreedingAge = -(int) ((1.0 - b) * AgeState.getMaxAge()) + 1;
                        villager.setBreedingAge(villagerBreedingAge);
                        villager.calculateDimensions();
                    }));
                    y += 28;
                }

                //relations
                for (String who : new String[]{"father", "mother", "spouse"}) {
                    textFieldWidget = addDrawableChild(new NamedTextFieldWidget(this.textRenderer, width / 2, y, DATA_WIDTH, 18,
                            Text.translatable("gui.villager_editor.relation." + who)));
                    textFieldWidget.setMaxLength(64);
                    textFieldWidget.setText(villagerData.getString("tree_" + who + "_name"));
                    textFieldWidget.setChangedListener(name -> villagerData.putString("tree_" + who + "_new", name));
                    y += 20;
                }

                //UUID
                y += 4;
                textFieldWidget = addDrawableChild(new TextFieldWidget(this.textRenderer, width / 2, y, DATA_WIDTH, 18, Text.literal("UUID")));
                textFieldWidget.setMaxLength(64);
                textFieldWidget.setText(villagerUUID.toString());
            }
            case "body" -> {
                //genes
                if (!Config.getServerConfig().allowPlayerSizeAdjustment && villagerUUID.equals(playerUUID)) {
                    y = doubleGeneSliders(y, Genetics.BREAST, Genetics.SKIN);
                    genetics.setGene(Genetics.SIZE, 0.80f);
                    genetics.setGene(Genetics.WIDTH, 0.80f);
                } else {
                    y = doubleGeneSliders(y, Genetics.SIZE, Genetics.WIDTH, Genetics.BREAST, Genetics.SKIN);
                }

                //clothes
                addDrawableChild(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Text.translatable("gui.villager_editor.randClothing"), b -> {
                    sendCommand("clothing");
                }));
                addDrawableChild(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Text.translatable("gui.villager_editor.selectClothing"), b -> {
                    setPage("clothing");
                }));
                y += 22;
                addDrawableChild(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Text.translatable("gui.villager_editor.prev"), b -> {
                    NbtCompound compound = new NbtCompound();
                    compound.putInt("offset", -1);
                    sendCommand("clothing", compound);
                }));
                addDrawableChild(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Text.translatable("gui.villager_editor.next"), b -> {
                    NbtCompound compound = new NbtCompound();
                    compound.putInt("offset", 1);
                    sendCommand("clothing", compound);
                }));
                y += 22;

                //skin color
                addDrawableChild(new ColorPickerWidget(width / 2 + margin, y, DATA_WIDTH - margin * 2, DATA_WIDTH - margin * 2,
                        genetics.getGene(Genetics.HEMOGLOBIN),
                        genetics.getGene(Genetics.MELANIN),
                        MCA.locate("textures/colormap/villager_skin.png"),
                        (vx, vy) -> {
                            genetics.setGene(Genetics.HEMOGLOBIN, vx.floatValue());
                            genetics.setGene(Genetics.MELANIN, vy.floatValue());
                        }));
            }
            case "head" -> {
                // HSV Hair selector
                addDrawableChild(new TooltipButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20,
                        Text.translatable(hsvColoredHair ? "gui.villager_editor.hair_hsv" : "gui.villager_editor.hair_genetic"),
                        Text.translatable("gui.villager_editor.hair_mode.tooltip"),
                        b -> {
                            hsvColoredHair = !hsvColoredHair;
                            init();
                        }));

                //genes
                y = doubleGeneSliders(y, Genetics.FACE);
                y = doubleGeneSliders(y, Genetics.VOICE_TONE, Genetics.VOICE);

                //hair
                addDrawableChild(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Text.translatable("gui.villager_editor.randHair"), b -> {
                    sendCommand("hair");
                }));
                addDrawableChild(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Text.translatable("gui.villager_editor.selectHair"), b -> {
                    setPage("hair");
                }));
                y += 22;
                addDrawableChild(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Text.translatable("gui.villager_editor.prev"), b -> {
                    NbtCompound compound = new NbtCompound();
                    compound.putInt("offset", -1);
                    sendCommand("hair", compound);
                }));
                addDrawableChild(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Text.translatable("gui.villager_editor.next"), b -> {
                    NbtCompound compound = new NbtCompound();
                    compound.putInt("offset", 1);
                    sendCommand("hair", compound);
                }));
                y += 22;

                //hair color
                if (hsvColoredHair) {
                    //hue
                    color.hueWidget = addDrawableChild(new HorizontalColorPickerWidget(width / 2 + 20, y, DATA_WIDTH - 40, 15,
                            color.hue / 360.0,
                            MCA.locate("textures/colormap/hue.png"),
                            (vx, vy) -> {
                                color.setHSV(
                                        vx * 360,
                                        color.saturation,
                                        color.brightness
                                );
                                refreshHairColor();
                            }));

                    //saturation
                    color.saturationWidget = addDrawableChild(new HorizontalGradientWidget(width / 2 + 20, y + 20, DATA_WIDTH - 40, 15,
                            color.saturation,
                            () -> {
                                double[] doubles = ClientUtils.HSV2RGB(color.hue, 0.0, 1.0);
                                return new float[]{
                                        (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                                };
                            },
                            () -> {
                                double[] doubles = ClientUtils.HSV2RGB(color.hue, 1.0, 1.0);
                                return new float[]{
                                        (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                                };
                            },
                            (vx, vy) -> {
                                color.setHSV(
                                        color.hue,
                                        vx,
                                        color.brightness
                                );
                                refreshHairColor();
                            }));


                    //brightness
                    color.brightnessWidget = addDrawableChild(new HorizontalGradientWidget(width / 2 + 20, y + 40, DATA_WIDTH - 40, 15,
                            color.brightness,
                            () -> {
                                double[] doubles = ClientUtils.HSV2RGB(color.hue, color.saturation, 0.0);
                                return new float[]{
                                        (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                                };
                            },
                            () -> {
                                double[] doubles = ClientUtils.HSV2RGB(color.hue, color.saturation, 1.0);
                                return new float[]{
                                        (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                                };
                            },
                            (vx, vy) -> {
                                color.setHSV(
                                        color.hue,
                                        color.saturation,
                                        vx
                                );
                                refreshHairColor();
                            }));

                    y += 65;

                    // Clear hair
                    addDrawableChild(new ButtonWidget(width / 2, y, DATA_WIDTH, 20,
                            Text.translatable("gui.villager_editor.clear_hair"),
                            b -> {
                                villager.clearHairDye();
                                init();
                            }));
                } else {
                    addDrawableChild(new ColorPickerWidget(width / 2 + margin, y, DATA_WIDTH - margin * 2, DATA_WIDTH - margin * 2,
                            genetics.getGene(Genetics.PHEOMELANIN),
                            genetics.getGene(Genetics.EUMELANIN),
                            MCA.locate("textures/colormap/villager_hair.png"),
                            (vx, vy) -> {
                                genetics.setGene(Genetics.PHEOMELANIN, vx.floatValue());
                                genetics.setGene(Genetics.EUMELANIN, vy.floatValue());
                            }));
                }
            }
            case "personality" -> {
                //personality
                List<ButtonWidget> personalityButtons = new LinkedList<>();
                int row = 0;
                final int BUTTONS_PER_ROW = 2;
                for (Personality p : Personality.values()) {
                    if (p != Personality.UNASSIGNED) {
                        if (row == BUTTONS_PER_ROW) {
                            row = 0;
                            y += 19;
                        }
                        ButtonWidget widget = addDrawableChild(new ButtonWidget(width / 2 + DATA_WIDTH / BUTTONS_PER_ROW * row, y, DATA_WIDTH / BUTTONS_PER_ROW, 20, p.getName(), b -> {
                            villager.getVillagerBrain().setPersonality(p);
                            personalityButtons.forEach(v -> v.active = true);
                            b.active = false;
                        }));
                        widget.active = p != villager.getVillagerBrain().getPersonality();
                        personalityButtons.add(widget);
                        row++;
                    }
                }
            }
            case "traits" -> {
                //traits
                addDrawableChild(new ButtonWidget(width / 2, y, 32, 20, Text.literal("<"), b -> setTraitPage(traitPage - 1)));
                addDrawableChild(new ButtonWidget(width / 2 + DATA_WIDTH - 32, y, 32, 20, Text.literal(">"), b -> setTraitPage(traitPage + 1)));
                addDrawableChild(new ButtonWidget(width / 2 + 32, y, DATA_WIDTH - 32 * 2, 20, Text.translatable("gui.villager_editor.page", traitPage + 1), b -> traitPage++));
                y += 22;
                Traits.Trait[] traits = getValidTraits();
                for (int i = 0; i < TRAITS_PER_PAGE; i++) {
                    int index = i + traitPage * TRAITS_PER_PAGE;
                    if (index < traits.length) {
                        Traits.Trait t = traits[index];
                        MutableText name = t.getName().copy().formatted(villager.getTraits().hasTrait(t) ? Formatting.GREEN : Formatting.GRAY);
                        addDrawableChild(new ButtonWidget(width / 2, y, DATA_WIDTH, 20, name, b -> {
                            if (villager.getTraits().hasTrait(t)) {
                                villager.getTraits().removeTrait(t);
                            } else {
                                villager.getTraits().addTrait(t);
                            }
                            b.setMessage(t.getName().copy().formatted(villager.getTraits().hasTrait(t) ? Formatting.GREEN : Formatting.GRAY));
                        }));
                        y += 20;
                    } else {
                        break;
                    }
                }
            }
            case "debug" -> {
                //profession
                boolean right = false;
                List<ButtonWidget> professionButtons = new LinkedList<>();
                for (VillagerProfession p : new VillagerProfession[]{
                        VillagerProfession.NONE,
                        ProfessionsMCA.GUARD.get(),
                        ProfessionsMCA.ARCHER.get(),
                        ProfessionsMCA.OUTLAW.get(),
                        ProfessionsMCA.ADVENTURER.get(),
                        ProfessionsMCA.CULTIST.get(),
                }) {
                    MutableText text = Text.translatable("entity.minecraft.villager." + p);
                    ButtonWidget widget = addDrawableChild(new ButtonWidget(width / 2 + (right ? DATA_WIDTH / 2 : 0), y, DATA_WIDTH / 2, 20, text, b -> {
                        NbtCompound compound = new NbtCompound();
                        compound.putString("profession", Registry.VILLAGER_PROFESSION.getId(p).toString());
                        syncVillagerData();
                        NetworkHandler.sendToServer(new VillagerEditorSyncRequest("profession", villagerUUID, compound));
                        requestVillagerData();
                        professionButtons.forEach(button -> button.active = true);
                        b.active = false;
                    }));
                    professionButtons.add(widget);
                    widget.active = villager.getProfession() != p;
                    if (right) {
                        y += 20;
                    }
                    right = !right;
                }
                y += 4;

                //infection
                addDrawableChild(new GeneSliderWidget(width / 2, y, DATA_WIDTH, 20, Text.translatable("gui.villager_editor.infection"), villager.getInfectionProgress(), b -> {
                    villager.setInfected(b > 0);
                    villager.setInfectionProgress(b.floatValue());
                }));
                y += 22;

                //hearts
                assert client != null;
                assert client.player != null;
                Memories player = villager.getVillagerBrain().getMemoriesForPlayer(client.player);
                y = integerChanger(y, player::modHearts, () -> Text.translatable("gui.blueprint.reputation", player.getHearts()));

                //mood
                integerChanger(y, v -> villager.getVillagerBrain().modifyMoodValue(v), () -> Text.translatable("gui.interact.label.mood", villager.getVillagerBrain().getMoodValue()));
            }
            case "clothing", "hair" -> {
                filterGender = villager.getGenetics().getGender();
                searchString = "";

                //search
                textFieldWidget = addDrawableChild(new TextFieldWidget(this.textRenderer, width / 2 - DATA_WIDTH / 2, height / 2 - 100, DATA_WIDTH, 18,
                        Text.translatable("gui.villager_editor.search")));
                textFieldWidget.setMaxLength(64);
                textFieldWidget.setChangedListener(v -> {
                    searchString = v;
                    filter();
                });
                y = height / 2 + 85;
                pageButtonWidget = addDrawableChild(new ButtonWidget(width / 2 - 30, y, 60, 20, Text.literal(""), b -> {
                }));
                addDrawableChild(new ButtonWidget(width / 2 - 32 - 28, y, 28, 20, Text.literal("<<"), b -> {
                    clothingPage = Math.max(0, clothingPage - 1);
                    updateClothingPageWidget();
                }));
                addDrawableChild(new ButtonWidget(width / 2 + 32, y, 28, 20, Text.literal(">>"), b -> {
                    clothingPage = Math.max(0, Math.min(clothingPageCount - 1, clothingPage + 1));
                    updateClothingPageWidget();
                }));
                addDrawableChild(new ButtonWidget(width / 2 + 32 + 32, y, 64, 20, Text.translatable("gui.button.done"), b -> {
                    if (page.equals("clothing")) {
                        setPage("body");
                    } else {
                        setPage("head");
                    }
                }));
                addDrawableChild(new ButtonWidget(width / 2 + 128, y, 64, 20, Text.translatable("gui.button.library"), b -> {
                    MinecraftClient.getInstance().setScreen(new SkinLibraryScreen(this, villagerVisualization));
                }));
                widgetMasculine = addDrawableChild(new ButtonWidget(width / 2 - 32 - 96 - 64, y, 64, 20, Text.translatable("gui.villager_editor.masculine"), b -> {
                    filterGender = Gender.MALE;
                    filter();
                    widgetMasculine.active = false;
                    widgetFeminine.active = true;
                }));
                widgetMasculine.active = filterGender != Gender.MALE;
                widgetFeminine = addDrawableChild(new ButtonWidget(width / 2 - 32 - 96 - 64 + 64, y, 64, 20, Text.translatable("gui.villager_editor.feminine"), b -> {
                    filterGender = Gender.FEMALE;
                    filter();
                    widgetMasculine.active = true;
                    widgetFeminine.active = false;
                }));
                widgetFeminine.active = filterGender != Gender.FEMALE;
                filter();
            }
        }
    }

    private void refreshHairColor() {
        if (villager.getHairDye()[0] == 0.0f) {
            color.setHSV(0.0, 0.5, 0.5);
        }
        villager.setHairDye(
                Math.max(1.0f / 255.0f, (float) color.red),
                Math.max(1.0f / 255.0f, (float) color.green),
                Math.max(1.0f / 255.0f, (float) color.blue)
        );
    }

    private Traits.Trait[] getValidTraits() {
        return (Traits.Trait.values().stream()).filter(e -> {
            if (villagerUUID.equals(playerUUID)) {
                return (Config.getInstance().bypassTraitRestrictions || e.isUsableOnPlayer()) && e.isEnabled();
            }
            return e.isEnabled();
        }).toList().toArray(Traits.Trait[]::new);
    }

    private void updateClothingPageWidget() {
        if (pageButtonWidget != null) {
            pageButtonWidget.setMessage(Text.literal(String.format("%d / %d", clothingPage + 1, clothingPageCount)));
        }
    }

    private void filter() {
        if (Objects.equals(page, "clothing")) {
            filteredClothing = filter(getClothing());
        } else {
            filteredHair = filter(getHair());
        }
    }

    private <T extends SkinListEntry> List<String> filter(HashMap<String, T> map) {
        List<String> filtered = map.entrySet().stream()
                .filter(v -> filterGender == v.getValue().getGender() || v.getValue().getGender() == Gender.NEUTRAL)
                .filter(v -> {
                    if (v.getValue() instanceof Clothing c) {
                        return !c.exclude;
                    } else {
                        return true;
                    }
                })
                .filter(v -> MCA.isBlankString(searchString) || v.getKey().contains(searchString))
                .map(Map.Entry::getKey)
                .toList();

        clothingPageCount = (int) Math.ceil(filtered.size() / ((float) CLOTHES_PER_PAGE));
        clothingPage = Math.max(0, Math.min(clothingPage, clothingPageCount - 1));

        updateClothingPageWidget();

        return filtered;
    }

    protected String[] getPages() {
        if (villagerUUID.equals(playerUUID)) {
            return new String[]{"general", "body", "head", "traits"};
        } else {
            return new String[]{"general", "body", "head", "personality", "traits", "debug"};
        }
    }

    protected void drawName(int x, int y) {
        drawName(x, y, name -> {
            this.updateName(name);
            if (doneWidget != null) {
                doneWidget.active = !MCA.isBlankString(name);
            }
        });
    }

    protected void drawName(int x, int y, Consumer<String> onChanged) {
        villagerNameField = addDrawableChild(new TextFieldWidget(this.textRenderer, x, y, DATA_WIDTH / 3 * 2, 18, Text.translatable("structure_block.structure_name")));
        villagerNameField.setMaxLength(32);
        villagerNameField.setText(getName().getString());
        villagerNameField.setChangedListener(onChanged);
        addDrawableChild(new ButtonWidget(x + DATA_WIDTH / 3 * 2 + 1, y - 1, DATA_WIDTH / 3 - 2, 20, Text.translatable("gui.button.random"), b ->
                NetworkHandler.sendToServer(new VillagerNameRequest(villager.getGenetics().getGender()))
        ));
    }

    public Text getName() {
        Text villagerName = null;
        boolean isPlayer = villagerUUID.equals(playerUUID);
        if (isPlayer) {
            assert client != null;
            assert client.player != null;
            villagerName = client.player.getCustomName();
        } else if (villager.hasCustomName()) {
            villagerName = villager.getCustomName();
        }

        if (villagerName == null || MCA.isBlankString(villagerName.getString())) {
            // Failsafe-conditions for non-present custom names
            if (isPlayer) {
                assert client != null;
                assert client.player != null;
                villagerName = client.player.getName();
            } else {
                villagerName = villager.getName();
            }

            if (villagerName == null || MCA.isBlankString(villagerName.getString())) {
                NetworkHandler.sendToServer(new VillagerNameRequest(villager.getGenetics().getGender()));
            } else {
                updateName(villagerName.getString());
            }
        }
        return villagerName;
    }

    public void updateName(String name) {
        if (!MCA.isBlankString(name)) {
            Text newName = Text.of(name);
            boolean isPlayer = villagerUUID.equals(playerUUID);
            if (isPlayer) {
                assert client != null;
                assert client.player != null;
                final Text realName = client.player.getName();
                if (realName.getString().equals(name)) {
                    // Remove Custom name if it is the same as our actual name
                    newName = null;
                }
                client.player.setCustomName(newName);
                client.player.setCustomNameVisible(newName != null);
                if (client.player.isCustomNameVisible()) {
                    villager.setCustomName(newName);
                } else {
                    villager.setName(realName.getString());
                }
            } else {
                villager.setCustomName(newName);
            }
        }
    }

    private ButtonWidget genderButtonFemale;
    private ButtonWidget genderButtonMale;

    void drawGender(int x, int y) {
        genderButtonFemale = new ButtonWidget(x, y, DATA_WIDTH / 2, 20, Text.translatable("gui.villager_editor.feminine"), sender -> {
            villager.getGenetics().setGender(Gender.FEMALE);
            sendCommand("gender");
            genderButtonFemale.active = false;
            genderButtonMale.active = true;
        });
        addDrawableChild(genderButtonFemale);

        genderButtonMale = new ButtonWidget(x + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Text.translatable("gui.villager_editor.masculine"), sender -> {
            villager.getGenetics().setGender(Gender.MALE);
            sendCommand("gender");
            genderButtonFemale.active = true;
            genderButtonMale.active = false;
        });
        addDrawableChild(genderButtonMale);

        genderButtonFemale.active = villager.getGenetics().getGender() != Gender.FEMALE;
        genderButtonMale.active = villager.getGenetics().getGender() != Gender.MALE;
    }

    void addModelSelectionWidgets(int x, int y) {
        if (allowPlayerModel && allowVillagerModel) {
            villagerSkinWidget = addDrawableChild(new TooltipButtonWidget(x, y, DATA_WIDTH / 3, 20, "gui.villager_editor.villager_skin", b -> {
                villagerData.putInt("playerModel", VillagerLike.PlayerModel.VILLAGER.ordinal());
                syncVillagerData();
                playerSkinWidget.active = true;
                villagerSkinWidget.active = false;
                vanillaSkinWidget.active = true;
            }));
            villagerSkinWidget.active = villagerData.getInt("playerModel") != VillagerLike.PlayerModel.VILLAGER.ordinal();

            playerSkinWidget = addDrawableChild(new TooltipButtonWidget(x + DATA_WIDTH / 3, y, DATA_WIDTH / 3, 20, "gui.villager_editor.player_skin", b -> {
                villagerData.putInt("playerModel", VillagerLike.PlayerModel.PLAYER.ordinal());
                syncVillagerData();
                playerSkinWidget.active = false;
                villagerSkinWidget.active = true;
                vanillaSkinWidget.active = true;
            }));
            playerSkinWidget.active = villagerData.getInt("playerModel") != VillagerLike.PlayerModel.PLAYER.ordinal();

            vanillaSkinWidget = addDrawableChild(new TooltipButtonWidget(x + DATA_WIDTH / 3 * 2, y, DATA_WIDTH / 3, 20, "gui.villager_editor.vanilla_skin", b -> {
                villagerData.putInt("playerModel", VillagerLike.PlayerModel.VANILLA.ordinal());
                syncVillagerData();
                villagerSkinWidget.active = true;
                playerSkinWidget.active = true;
                vanillaSkinWidget.active = false;
            }));
            vanillaSkinWidget.active = villagerData.getInt("playerModel") != VillagerLike.PlayerModel.VANILLA.ordinal();
        } else {
            addDrawableChild(new TooltipButtonWidget(x, y, DATA_WIDTH, 20, "gui.villager_editor.model_blacklist_hint", b -> {
            })).active = false;
        }
    }

    private void sendCommand(String command) {
        sendCommand(command, new NbtCompound());
    }

    private void sendCommand(String command, NbtCompound nbt) {
        syncVillagerData();
        NetworkHandler.sendToServer(new VillagerEditorSyncRequest(command, villagerUUID, nbt));
        requestVillagerData();
    }

    private void setTraitPage(int i) {
        Traits.Trait[] traits = getValidTraits();
        int maxPage = (int) Math.ceil((double) traits.length / TRAITS_PER_PAGE) - 1;
        traitPage = Math.max(0, Math.min(maxPage, i));
        setPage("traits");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page.equals("clothing") && (hoveredClothingId >= 0 && filteredClothing.size() > hoveredClothingId)) {
            villager.setClothes(filteredClothing.get(hoveredClothingId));
            setPage("body");
            eventCallback("clothing");
            return true;

        }

        if (page.equals("hair") && (hoveredClothingId >= 0 && filteredHair.size() > hoveredClothingId)) {
            villager.setHair(filteredHair.get(hoveredClothingId));
            setPage("head");
            eventCallback("hair");
            return true;

        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected void eventCallback(String event) {
        // nop
    }

    protected boolean shouldUsePlayerModel() {
        return false;
    }

    protected boolean shouldPrintPlayerHint() {
        return true;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);

        fill(matrices, 0, 20, width, height - 20, 0x66000000);

        if (villager == null) {
            return;
        }

        villager.age = (int) (System.currentTimeMillis() / 50L);

        if (shouldDrawEntity()) {
            int x = width / 2 - DATA_WIDTH / 2;
            int y = height / 2 + 70;
            if (villagerUUID.equals(playerUUID) && shouldUsePlayerModel()) {
                assert MinecraftClient.getInstance().player != null;
                InventoryScreen.drawEntity(x, y, 60, x - mouseX, y - 50 - mouseY, MinecraftClient.getInstance().player);
            } else {
                InventoryScreen.drawEntity(x, y, 60, x - mouseX, y - 50 - mouseY, villager);
            }

            // hint for confused people
            if (shouldPrintPlayerHint() && villagerUUID.equals(playerUUID) && villagerData.getInt("playerModel") != VillagerLike.PlayerModel.VILLAGER.ordinal()) {
                matrices.push();
                matrices.translate(x, y - 145, 0);
                matrices.scale(0.5f, 0.5f, 0.5f);
                drawCenteredText(matrices, textRenderer, Text.translatable("gui.villager_editor.model_hint"), 0, 0, 0xAAFFFFFF);
                matrices.pop();
            }
        }

        if (page.equals("clothing") || page.equals("hair")) {
            NbtCompound nbt = new NbtCompound();
            villager.writeCustomDataToNbt(nbt);
            villagerVisualization.readCustomDataFromNbt(nbt);
            villagerVisualization.setBreedingAge(villager.getBreedingAge());
            villagerVisualization.calculateDimensions();

            int i = 0;
            hoveredClothingId = -1;
            for (int y = 0; y < CLOTHES_V; y++) {
                for (int x = 0; x < CLOTHES_H + y; x++) {
                    int index = clothingPage * CLOTHES_PER_PAGE + i;
                    if ((page.equals("clothing") ? filteredClothing : filteredHair).size() > index) {
                        if (page.equals("clothing")) {
                            villagerVisualization.setClothes(filteredClothing.get(index));
                        } else {
                            villagerVisualization.setHair(filteredHair.get(index));
                        }

                        int cx = width / 2 + (int) ((x - CLOTHES_H / 2.0 + 0.5 - 0.5 * (y % 2)) * 40);
                        int cy = height / 2 + 25 + (int) ((y - CLOTHES_V / 2.0 + 0.5) * 65);

                        if (Math.abs(cx - mouseX) <= 20 && Math.abs(cy - mouseY - 30) <= 30) {
                            hoveredClothingId = index;
                        }

                        InventoryScreen.drawEntity(cx, cy, (hoveredClothingId == index) ? 35 : 30,
                                -(mouseX - cx) / 2.0f, -(mouseY - cy - 64) / 2.0f, villagerVisualization);
                        i++;
                    } else {
                        break;
                    }
                }
            }
        }

        super.render(matrices, mouseX, mouseY, delta);
    }

    protected boolean shouldDrawEntity() {
        return !page.equals("loading") && !page.equals("clothing") && !page.equals("hair");
    }

    protected boolean shouldShowPageSelection() {
        return !page.equals("clothing") && !page.equals("hair");
    }

    public void setVillagerName(String name) {
        villagerNameField.setText(name);
        updateName(name);
    }

    public void setVillagerData(NbtCompound villagerData) {
        if (villager != null) {
            this.villagerData = villagerData;
            villager.readCustomDataFromNbt(villagerData);

            float[] hairDye = villager.getHairDye();
            hsvColoredHair = hairDye[0] > 0.0f;
            color.setRGB(hairDye[0], hairDye[1], hairDye[2]);

            villagerBreedingAge = villagerData.getInt("Age");
            villager.setBreedingAge(villagerBreedingAge);
            if (client != null && client.player != null) {
                villager.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
                villagerVisualization.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
            }
            villager.calculateDimensions();
        }
        if (page.equals("loading")) {
            setPage("general");
        } else {
            setPage(page);
        }
    }

    private void requestVillagerData() {
        NetworkHandler.sendToServer(new GetVillagerRequest(villagerUUID));
    }

    public void syncVillagerData() {
        NbtCompound nbt = villagerData;
        ((MobEntity) villager).writeCustomDataToNbt(nbt);
        nbt.putInt("Age", villagerBreedingAge);
        NetworkHandler.sendToServer(new VillagerEditorSyncRequest("sync", villagerUUID, nbt));
    }

    public static void setSkinList(HashMap<String, Clothing> clothing, HashMap<String, Hair> hair) {
        VillagerEditorScreen.clothing = clothing;
        VillagerEditorScreen.hair = hair;
    }

    @Override
    public void skinListUpdatedCallback() {
        filter();
    }

    public static void sync() {
        if (isSkinListOutdated) {
            NetworkHandler.sendToServer(new SkinListRequest());
            isSkinListOutdated = false;
        }
    }

    public static HashMap<String, Clothing> getClothing() {
        sync();
        return clothing;
    }

    public static HashMap<String, Hair> getHair() {
        sync();
        return hair;
    }

    public static void setSkinListOutdated() {
        isSkinListOutdated = true;
    }

    public VillagerEntityMCA getVillager() {
        return villager;
    }
}
