public class Cat extends Animal {
    @Override
    public String eat(Food food) {
        return "cat eats "+food.eaten(this);
    }

}
