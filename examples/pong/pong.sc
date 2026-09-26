import "renderer.sc" as renderer;
import triangle::rgb;
import utils::*;
import sensing;
import math;

enum BotDifficulty(EASY, MEDIUM, IMPOSSIBLE);

sealed enum GameMode {
    Human, //human vs human
    Bot(BotDifficulty diff) //bot with difficulty
}

sealed enum Screen {
    Menu,
    Game(GameMode mode, int score, int leftPadY, int rightPadY, float ballX, float ballY, float ballVelX, float ballVelY),
    ModeChoose,
    Lose(int score)
}

Screen currentScreen = Screen.Menu;
bool mouseReleased = false;

on GreenFlag {
    renderer::init();
    while(true) {
        renderer::clear();
        renderer::fill(-240, -180, 240, 180, rgb(0, 0, 0));

        when(currentScreen) {
            Screen.Menu -> renderMenu();
            Screen.Game game -> {
                update(game);
                renderGame(game);
            }
            Screen.ModeChoose -> renderSelectModeScreen();
            Screen.Lose loser -> renderLose(loser.score);
        }

        wait(0);
    }
}

warp void renderMenu() {
    renderer::renderText("Pong", 0, 0, rgb(0, 255, 0), true);
    renderer::renderText("Click to play", 0, -80, rgb(0, 255, 0), true);

    if(sensing::isMousePressed()) {
        restart();
    }
}

warp void update(Screen.Game game) {
    updateKeys(game);
    moveBall(game);
}

warp void renderGame(Screen.Game game) {
    renderer::renderText("Score: ${game.score}", 0, 230, rgb(0, 255, 0), true);

    //left pad
    renderer::fill(
        -240,
        game.leftPadY - 50,

        -200,
        game.leftPadY + 50,
        rgb(255, 255, 255)
    );

    //right pad
    renderer::fill(
        240,
        game.rightPadY - 50,

        200,
        game.rightPadY + 50,
        rgb(255, 255, 255)
    );

    //ball
    renderer::fill(
        game.ballX - 10,
        game.ballY - 10,
        game.ballX + 10,
        game.ballY + 10,
        rgb(255, 255, 255)
    );
}

warp void restart() {
    mouseReleased = false;
    currentScreen = Screen.ModeChoose;
}

const int MOVE_SPEED = 15;

warp void updateKeys(Screen.Game game) {
    if(sensing::isKeyPressed("w")) {
        game.leftPadY += MOVE_SPEED;
        if(game.leftPadY >= 130) {
            game.leftPadY = 130;
        }
    }

    if(sensing::isKeyPressed("s")) {
        game.leftPadY -= MOVE_SPEED;
        if(game.leftPadY <= -130) {
            game.leftPadY = -130;
        }
    }

    when(game.mode) {
        GameMode.Human -> {
            if(sensing::isKeyPressed("up arrow")) {
                game.rightPadY += MOVE_SPEED;
                if(game.rightPadY >= 130) {
                    game.rightPadY = 130;
                }
            }

            if(sensing::isKeyPressed("down arrow")) {
                game.rightPadY -= MOVE_SPEED;
                if(game.rightPadY <= -130) {
                    game.rightPadY = -130;
                }
            }
        }
        GameMode.Bot bot -> {
            updateBot(game, bot.diff);
        }
    }
}

warp void updateBot(Screen.Game game, BotDifficulty diff) {
    when(diff) {
        BotDifficulty.IMPOSSIBLE -> {
            //as the name implies, this is impossible to beat.
            game.rightPadY = math::round(game.ballY);
        }
        BotDifficulty.EASY -> {
            if (game.ballVelX > 0 && game.ballX > 0) {
                if (game.ballY > game.rightPadY + 25) {
                    game.rightPadY += 4;
                } else if (game.ballY < game.rightPadY - 25) {
                    game.rightPadY -= 4;
                }
            }
        }
        BotDifficulty.MEDIUM -> {
            if (game.ballVelX > 0) {
                if (game.ballY > game.rightPadY + 10) {
                    game.rightPadY += 7;
                } else if (game.ballY < game.rightPadY - 10) {
                    game.rightPadY -= 7;
                }
            } else {
                if (game.rightPadY > 5) {
                    game.rightPadY -= 3;
                } else if (game.rightPadY < -5) {
                    game.rightPadY += 3;
                }
            }
        }
    }

    if(game.rightPadY <= -130) {
        game.rightPadY = -130;
    }
    if(game.rightPadY >= 130) {
        game.rightPadY = 130;
    }
}

const float BALL_SPEED = 6.0;

warp void moveBall(Screen.Game game) {
    game.ballX += game.ballVelX * BALL_SPEED;
    game.ballY += game.ballVelY * BALL_SPEED;

    if (game.ballY + 10 >= 180) {
        game.ballY = 170;
        game.ballVelY = -game.ballVelY;
    } else if (game.ballY - 10 <= -180) {
        game.ballY = -170;
        game.ballVelY = -game.ballVelY;
    }

    if (game.ballVelX < 0 && game.ballX - 10 <= -200 && game.ballX >= -240) {
        if (game.ballY + 10 >= game.leftPadY - 50 && game.ballY - 10 <= game.leftPadY + 50) {
            game.ballX = -190;
            game.ballVelX = -game.ballVelX;
            game.score += 1;
        }
    }

    if (game.ballVelX > 0 && game.ballX + 10 >= 200 && game.ballX <= 240) {
        if (game.ballY + 10 >= game.rightPadY - 50 && game.ballY - 10 <= game.rightPadY + 50) {
            game.ballX = 190;
            game.ballVelX = -game.ballVelX;
            game.score += 1;
        }
    }

    if (game.ballX < -240 || game.ballX > 240) {
        currentScreen = Screen.Lose(game.score);
    }
}

warp void renderLose(int score) {
    renderer::renderText("Game over! Score: ${score}", 0, 0, rgb(0, 255, 0), true);
    renderer::renderText("Click to restart!", 0, -80, rgb(0, 255, 0), true);

    if(sensing::isMousePressed()) {
        restart();
    }
}

warp void renderSelectModeScreen() {
    if (!sensing::isMousePressed()) {
        mouseReleased = true;
    }

    float green = rgb(0, 255, 0);

    renderer::renderText("Select game mode", 0, 134, green, true);
    renderer::button(
        "Human vs Human",
        -140, 61, 280, 44,
        rgb(81, 81, 81), green,
        () -> {
            startGame(GameMode.Human);
        }
    );

    renderer::renderText("Human vs Bot", 0, 32, green, true);

    renderer::button(
        "Easy",
        -140, -41, 280, 44,
        rgb(81, 81, 81), green,
        () -> {
            startGame(GameMode.Bot(BotDifficulty.EASY));
        }
    );

    renderer::button(
        "Medium",
        -140, -95, 280, 44,
        rgb(81, 81, 81), green,
        () -> {
            startGame(GameMode.Bot(BotDifficulty.MEDIUM));
        }
    );

    renderer::button(
        "Impossible",
        -140, -149, 280, 44,
        rgb(81, 81, 81), green,
        () -> {
            startGame(GameMode.Bot(BotDifficulty.IMPOSSIBLE));
        }
    );
}

warp void startGame(GameMode mode) {
    return if !mouseReleased;

    float determinedVelX = if(random(0, 1) == 0) -1 else 1;
    float determinedVelY = if(random(0, 1) == 0) -1 else 1;
    currentScreen = Screen.Game(
        mode, 0, 0, 0, 0, 0, determinedVelX, determinedVelY
    );
}